package cn.yibu.chess.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisTimingsTest {
    @Before fun clear() { AnalysisTimings.clear() }

    @Test fun monotonicDurationsKeepNestedStagesAndUiFrameSeparateFromServerTime() {
        var now = 50_000_000L
        val trace = AnalysisTiming(4, 8, "deep") { now }
        now = 650_000_000L; trace.duration("http_ms", trace.started)
        now = 700_000_000L; trace.duration("analysis_ms", trace.started)
        now = 800_000_000L; trace.duration("save_ms", 700_000_000L)
        now = 850_000_000L; trace.finish("success")
        now = 900_000_000L; AnalysisTimings.frame(trace.id)
        trace.finish("failed") // A late callback cannot duplicate or change the outcome.
        val row = AnalysisTimings.snapshot().getJSONArray("analysis").getJSONObject(0)
        assertEquals(600.0, row.getDouble("http_ms"), .001)
        assertEquals(650.0, row.getDouble("analysis_ms"), .001)
        assertEquals(100.0, row.getDouble("save_ms"), .001)
        assertEquals(800.0, row.getDouble("total_ms"), .001)
        assertEquals(850.0, row.getDouble("total_to_frame_ms"), .001)
        assertEquals(50.0, row.getDouble("after_completion_to_frame_ms"), .001)
        assertTrue(row.isNull("server_search_ms"))
        assertTrue(row.isNull("server_cached"))
        assertEquals("success", row.getString("status"))
        assertEquals(1, AnalysisTimings.snapshot().getJSONArray("analysis").length())
    }

    @Test fun optionalStatsOnlyAcceptWhitelistedFiniteMeasurementsAndNeverRecordBodyFields() {
        val trace = AnalysisTiming(1, 1, "deep")
        trace.serverStats(Json.parseToJsonElement("""{"searchTimeMs":600,"queueWaitMs":0,"responseTimeMs":620,"cached":false,"token":"secret-token","body":"secret-moves"}"""))
        trace.serverStats(Json.parseToJsonElement("""{"searchTimeMs":-1,"queueWaitMs":100000000,"responseTimeMs":"Infinity"}"""))
        trace.serverStats(Json.parseToJsonElement(""""malformed optional stats""""))
        val row = trace.snapshot()
        assertEquals(600.0, row.getDouble("server_search_ms"), .001)
        assertEquals(0.0, row.getDouble("server_queue_ms"), .001)
        assertEquals(620.0, row.getDouble("server_response_ms"), .001)
        assertFalse(row.getBoolean("server_cached"))
        assertFalse(row.toString().contains("secret-"))
    }

    @Test fun serverTimingHeaderIgnoresDescriptionsAndMalformedDurations() {
        val trace = AnalysisTiming(1, 2, "deep")
        trace.serverTiming("search;dur=600.5;desc=secret-token, queue;dur=12, total;dur=620, password;dur=300")
        trace.serverTiming("search;dur=NaN, queue;dur=-30, total;dur=Infinity")
        val row = trace.snapshot()
        assertEquals(600.5, row.getDouble("server_search_ms"), .001)
        assertEquals(12.0, row.getDouble("server_queue_ms"), .001)
        assertFalse(row.toString().contains("secret-token"))
        assertFalse(row.has("password"))
    }

    @Test fun exportedRecordCountsAreBoundedAndUnknownTimingsAreNotInventedZeros() {
        repeat(AnalysisTimings.LIMIT + 3) { i -> AnalysisTiming(1, i + 1, "deep").finish("success") }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = JSONObject(AnalysisTimings.export(context))
        val rows = report.getJSONObject("timings").getJSONArray("analysis")
        assertEquals(AnalysisTimings.LIMIT, rows.length())
        assertEquals(4, rows.getJSONObject(0).getInt("ply"))
        assertTrue(rows.getJSONObject(0).isNull("http_ms"))
        assertTrue(rows.getJSONObject(0).isNull("total_to_frame_ms"))
        assertFalse(report.has("token"))
        assertEquals("current_process_last_120", report.getJSONObject("timings").getString("scope"))
    }

    @Test fun snapshotAndFinishingParallelRequestsDoNotDeadlockOrMixIds() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writer = pool.submit { repeat(50) { AnalysisTiming(1, it, "deep").finish("success") } }
            val reader = pool.submit { repeat(50) { AnalysisTimings.snapshot() } }
            writer.get(5, TimeUnit.SECONDS); reader.get(5, TimeUnit.SECONDS)
            val rows = AnalysisTimings.snapshot().getJSONArray("analysis")
            assertEquals(50, rows.length())
            assertEquals(50, (0 until rows.length()).map { rows.getJSONObject(it).getString("request_id") }.toSet().size)
        } finally { pool.shutdownNow() }
    }
}
