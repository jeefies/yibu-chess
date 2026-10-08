package cn.yibu.chess.engine

import cn.yibu.chess.core.*
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
import java.util.concurrent.TimeUnit

class RemoteStockfishClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var client: RemoteStockfishClient
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        client = RemoteStockfishClient({ " test-token " }, server.url("/").toString(), http)
    }
    @After fun close() {
        client.stop()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }
    private fun item(move: String, depth: Int = 22, value: Int = 34): String =
        """{"move":"$move","depth":$depth,"score":{"type":"cp","value":$value},"pv":["$move"]}"""
    private fun analysis(played: String = item("c7c5", value = 28), comparison: String =
        """{"canCompare":true,"commonDepth":22}"""): String =
        """{"best":${item("e7e5")},"played":$played,"second":${item("c7c5", value = 28)},"previousBest":${item("e7e5", 21)},"comparison":$comparison,"engine":{"name":"Stockfish","version":"19"}}"""
    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body))

    @Test fun analyzeSendsFullHistoryAndMapsBlackRootScoresAndEngineVersion() = runBlocking {
        enqueue(analysis())
        val result = client.analyzeMove(listOf("e2e4"), "c7c5", true)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/sf/v1/analyze-move", request.path)
        assertEquals("test-token", request.getHeader("X-Access-Token"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(ChessRules.START_FEN, body.getValue("position").jsonObject.getValue("initialFen").jsonPrimitive.content)
        assertEquals(listOf("e2e4"), body.getValue("position").jsonObject.getValue("moves").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("c7c5", body.getValue("playedMove").jsonPrimitive.content)
        assertEquals("deep", body.getValue("profile").jsonPrimitive.content)
        assertEquals(2, body.getValue("multiPv").jsonPrimitive.int)
        assertEquals(34, result.best.cp)
        assertEquals(-.34, result.best.whiteScore(false), .0001)
        assertEquals("Stockfish 19", result.engineName)
        assertEquals(2, result.second!!.multiPv)
        assertEquals(21, result.previousBest!!.depth)
        assertEquals(0, result.best.win + result.best.draw + result.best.loss)
        assertTrue(result.best.expected > .5)
    }

    @Test fun evaluateReturnsLegalMoveAndUsesStandardProfile() = runBlocking {
        enqueue("""{"completedDepth":22,"best":${item("e2e4")},"candidates":[${item("e2e4")}],"engine":{"name":"Stockfish","version":"19"}}""")
        assertEquals("e2e4", Opponent(client).move(emptyList()))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("standard", body.getValue("profile").jsonPrimitive.content)
        assertEquals(1, body.getValue("multiPv").jsonPrimitive.int)
    }

    @Test fun missingPlayedIsAnErrorInsteadOfCopyingTheBestEvaluation() = runBlocking {
        enqueue(analysis(played = "null"))
        val failure = runCatching { client.analyzeMove(listOf("e2e4"), "c7c5", true) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("实战走法"))
    }

    @Test fun mismatchedPlayedPvAndMissingScoresAreRejected() = runBlocking {
        enqueue(analysis(played = item("e7e5")))
        assertTrue(runCatching { client.analyzeMove(listOf("e2e4"), "c7c5", true) }.isFailure)
        enqueue(analysis(played = """{"depth":22,"pv":["c7c5"]}"""))
        assertTrue(runCatching { client.analyzeMove(listOf("e2e4"), "c7c5", true) }.isFailure)
    }

    @Test fun illegalContinuationIsRejected() = runBlocking {
        enqueue(analysis(played = """{"depth":22,"score":{"type":"cp","value":20},"pv":["c7c5","e2e5"]}"""))
        assertTrue(runCatching { client.analyzeMove(listOf("e2e4"), "c7c5", true) }.isFailure)
    }

    @Test fun incomparableAndBoundScoresRemainProvisional() = runBlocking {
        enqueue(analysis(comparison = """{"canCompare":false,"commonDepth":null}"""))
        val review = MoveAnalyzer(client).analyze(listOf("e2e4"), "c7c5", true)
        assertEquals(Grade.UNSTABLE, review.grade)
        assertTrue(review.provisional)
        enqueue(analysis(played = """{"depth":22,"score":{"type":"cp","value":28,"bound":"lower"},"pv":["c7c5"]}"""))
        assertEquals(Grade.UNSTABLE, MoveAnalyzer(client).analyze(listOf("e2e4"), "c7c5", true).grade)
    }

    @Test fun healthRequiresReadyStateAndHttpErrorsAreReadable() = runBlocking {
        enqueue("""{"status":"ready","engine":{"name":"Stockfish","version":"19"}}""")
        assertEquals("Stockfish 19", client.checkHealth().getOrThrow())
        enqueue("""{"status":"unavailable"}""")
        assertTrue(client.checkHealth().isFailure)
        for (code in listOf(401, 429, 503)) {
            enqueue("""{"detail":"test-token"}""", code)
            val message = client.checkHealth().exceptionOrNull()?.message.orEmpty()
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("test-token"))
        }
    }

    @Test fun cancellationPropagatesAndNextRequestStillWorks() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async { client.checkHealth() }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        enqueue("""{"status":"ready","engine":{"name":"Stockfish","version":"19"}}""")
        assertTrue(client.checkHealth().isSuccess)
    }

    @Test fun brokenResponseBodyFailsPromptlyWithoutLosingTheContinuation() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(16384)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(withTimeout(3000) { client.checkHealth() }.isFailure)
    }

    @Test fun emptyTokenDoesNotSendARequest() = runBlocking {
        val empty = RemoteStockfishClient({ "" }, server.url("/").toString(), http)
        assertTrue(empty.checkHealth().isFailure)
        assertEquals(0, server.requestCount)
    }
}
