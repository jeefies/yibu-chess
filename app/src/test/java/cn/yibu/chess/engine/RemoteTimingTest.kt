package cn.yibu.chess.engine

import cn.yibu.chess.diagnostics.AnalysisTiming
import cn.yibu.chess.diagnostics.AnalysisTimings
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteTimingTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var client: RemoteStockfishClient
    @Before fun setup() {
        AnalysisTimings.clear()
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        client = RemoteStockfishClient({ "private-access-token" }, server.url("/").toString(), http)
    }
    @After fun close() {
        client.stop(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow(); server.shutdown()
    }
    private fun body(stats: String = "null") = """{"best":{"depth":22,"score":{"type":"cp","value":34},"pv":["e7e5"]},"played":{"depth":22,"score":{"type":"cp","value":28},"pv":["c7c5"]},"comparison":{"canCompare":true,"commonDepth":22},"stats":$stats}"""
    private fun trace() = AnalysisTiming(1, 2, "deep", System::nanoTime)

    @Test fun lightningExplicitlyLimitsBothDepthAndBudgetAndRecordsTheActualProfile() = runBlocking {
        server.enqueue(MockResponse().setBody(body()))
        val trace = trace()
        withContext(trace) { client.analyzeMove(listOf("e2e4"), "c7c5", true, "lightning") }
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("lightning", payload.getValue("profile").jsonPrimitive.content)
        assertEquals(2, payload.getValue("multiPv").jsonPrimitive.int)
        val limits = payload.getValue("limits").jsonObject
        assertEquals(22, limits.getValue("depth").jsonPrimitive.int)
        assertEquals(500, limits.getValue("maxTimeMs").jsonPrimitive.int)
        assertEquals(trace.id, request.getHeader("X-Request-ID"))
        assertEquals("lightning", trace.snapshot().getString("profile"))
        assertEquals(22, trace.snapshot().getInt("target_depth"))
        assertEquals(500, trace.snapshot().getInt("search_budget_ms"))
    }

    @Test fun delayedHttpIsMeasuredSeparatelyFromServerSearchAndRequestIdMatchesTheHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(body("""{"searchTimeMs":20,"queueWaitMs":0,"responseTimeMs":25,"cached":false}"""))
            .setHeadersDelay(180, TimeUnit.MILLISECONDS))
        val trace = trace()
        withContext(trace) { assertEquals(22, client.analyzeMove(listOf("e2e4"), "c7c5", true).best.depth) }
        trace.finish("success")
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(trace.id, request.getHeader("X-Request-ID"))
        val row = trace.snapshot()
        assertTrue(row.getDouble("http_ms") >= 140)
        assertTrue(row.getDouble("response_headers_wait_ms") >= 140)
        assertEquals(20.0, row.getDouble("server_search_ms"), .001)
        assertTrue(row.getDouble("response_processing_ms") >= 0)
        assertEquals(200, row.getInt("http_status"))
        assertFalse(row.toString().contains("private-access-token"))
    }

    @Test fun warmConnectionIsReusedAndAbsentOrMalformedServerStatsDoNotBreakAnalysis() = runBlocking {
        server.enqueue(MockResponse().setBody(body()).setHeader("Server-Timing", "search;dur=8,queue;dur=0"))
        val first = trace()
        withContext(first) { client.analyzeMove(listOf("e2e4"), "c7c5", true) }
        assertEquals(8.0, first.snapshot().getDouble("server_search_ms"), .001)
        server.enqueue(MockResponse().setBody(body("""{"searchTimeMs":-1,"cached":"bad"}""")))
        val second = trace()
        withContext(second) { client.analyzeMove(listOf("e2e4"), "c7c5", true) }
        val row = second.snapshot()
        assertTrue(row.getBoolean("connection_reused"))
        assertTrue(row.isNull("connect_ms"))
        assertTrue(row.isNull("server_search_ms"))
        assertTrue(row.isNull("server_cached"))
        assertEquals("ipv4", row.getString("ip_family"))
    }

    @Test fun concurrentCallsKeepTheirOwnTimingsAndCorrelationIds() = runBlocking {
        server.enqueue(MockResponse().setBody(body("""{"searchTimeMs":17}""")))
        server.enqueue(MockResponse().setBody(body("""{"searchTimeMs":42}""")))
        val traces = listOf(trace(), trace())
        traces.map { trace -> async(trace) { client.analyzeMove(listOf("e2e4"), "c7c5", true) } }.awaitAll()
        val requests = listOf(server.takeRequest(2, TimeUnit.SECONDS)!!, server.takeRequest(2, TimeUnit.SECONDS)!!)
        assertEquals(traces.map { it.id }.toSet(), requests.map { it.getHeader("X-Request-ID") }.toSet())
        assertEquals(setOf(17.0, 42.0), traces.map { it.snapshot().getDouble("server_search_ms") }.toSet())
    }

    @Test fun cancellationStillFinishesTheHttpSpanAndDoesNotCaptureTheToken() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val trace = trace()
        val pending = async(trace) { client.analyzeMove(listOf("e2e4"), "c7c5", true) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertTrue(trace.snapshot().getDouble("http_ms") >= 0)
        assertFalse(trace.snapshot().toString().contains("private-access-token"))
    }
}
