package cn.yibu.chess

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import cn.yibu.chess.data.PlayPreferences
import cn.yibu.chess.data.GameDatabase
import cn.yibu.chess.diagnostics.AnalysisTimings
import cn.yibu.chess.engine.RemoteStockfishClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisTimingViewModelTest {
    @Before fun resetDatabase() { GameDatabase.resetForTests() }
    @After fun closeDatabase() { GameDatabase.resetForTests() }
    private fun waitFor(model: GameViewModel, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); Thread.sleep(20)
        }
        assertTrue("State did not settle: ${model.state.value.status}, ${model.state.value.error}", condition())
    }

    @Test fun successfulReviewRecordsDatabaseAndReloadCostAndExportsViaPrivateFileProvider() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val server = MockWebServer().apply { start() }
        val remote = RemoteStockfishClient({ "secret-access-token" }, server.url("/").toString())
        val model = GameViewModel(app, remote)
        val store = ViewModelStore().apply { put("timing", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            model.load(GameRecord(moves = listOf("e2e4", "c7c5"), finished = true))
            model.saveSettings(PlaySettings(color = ColorPreference.WHITE, stockfishToken = "secret-access-token"))
            AnalysisTimings.clear()
            server.enqueue(MockResponse().setBody("""{"best":{"depth":22,"score":{"type":"cp","value":34},"pv":["e7e5"]},"played":{"depth":22,"score":{"type":"cp","value":28},"pv":["c7c5"]},"comparison":{"canCompare":true,"commonDepth":22},"stats":{"searchTimeMs":600,"queueWaitMs":0,"cached":false}}"""))
            model.analyzeSelected()
            waitFor(model) { !model.state.value.busy && model.state.value.game.reviews.isNotEmpty() }
            waitFor(model) { AnalysisTimings.snapshot().getJSONArray("record_reloads").length() > 0 }
            assertNull(model.state.value.error)
            val row = AnalysisTimings.snapshot().getJSONArray("analysis").getJSONObject(0)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals(row.getString("request_id"), request.getHeader("X-Request-ID"))
            assertEquals(row.getString("request_id"), model.state.value.lastAnalysisTimingId)
            assertEquals("success", row.getString("status"))
            assertEquals("deep", row.getString("profile"))
            assertEquals(600.0, row.getDouble("server_search_ms"), .001)
            for (field in listOf("analysis_ms", "http_ms", "response_processing_ms", "save_ms", "database_ms", "total_ms"))
                assertTrue("Missing or negative $field", row.getDouble(field) >= 0)
            val chooser = model.shareAnalysisTimings()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("application/json", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val exported = File(app.cacheDir, "exports/yibu-analysis-timings.json").readText()
            assertFalse(exported.contains("secret-access-token"))
            assertFalse(exported.contains("c7c5"))
            assertEquals(BuildConfig.VERSION_NAME, JSONObject(exported).getString("version"))
        } finally { store.clear(); server.shutdown() }
    }

    @Test fun navigationCancelsAndRecordsTheAttemptWithoutUpdatingTheNewPage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val server = MockWebServer().apply { start() }
        val remote = RemoteStockfishClient({ "secret-access-token" }, server.url("/").toString())
        val model = GameViewModel(app, remote)
        val store = ViewModelStore().apply { put("timing", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            model.load(GameRecord(moves = listOf("e2e4", "c7c5"), finished = true))
            model.saveSettings(PlaySettings(color = ColorPreference.WHITE, stockfishToken = "secret-access-token"))
            AnalysisTimings.clear()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            model.analyzeSelected()
            waitFor(model) { server.requestCount == 1 }
            model.page(2)
            waitFor(model) { AnalysisTimings.snapshot().getJSONArray("analysis").length() == 1 }
            val row = AnalysisTimings.snapshot().getJSONArray("analysis").getJSONObject(0)
            assertEquals("cancelled", row.getString("status"))
            assertTrue(row.isNull("save_ms"))
            assertTrue(model.state.value.game.reviews.isEmpty())
            assertEquals(2, model.state.value.page)
            assertNull(model.state.value.error)
        } finally { store.clear(); server.shutdown() }
    }
}
