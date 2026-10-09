package cn.yibu.chess

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import cn.yibu.chess.data.GameDatabase
import cn.yibu.chess.data.GameRepository
import cn.yibu.chess.data.PlayPreferences
import cn.yibu.chess.diagnostics.AnalysisTimings
import cn.yibu.chess.engine.RemoteStockfishClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundAnalysisTest {
    @Before fun reset() { GameDatabase.resetForTests(); AnalysisTimings.clear() }
    @After fun close() { GameDatabase.resetForTests() }
    private fun waitFor(model: GameViewModel, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); Thread.sleep(20)
        }
        assertTrue("State did not settle: ${model.state.value.status}, ${model.state.value.error}", condition())
    }
    private fun settle() {
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); Thread.sleep(10) }
    }
    private fun response(request: RecordedRequest, comparable: Boolean = true): MockResponse {
        val payload = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
        val move = payload.getValue("playedMove").jsonPrimitive.content
        val item = """{"move":"$move","depth":22,"score":{"type":"cp","value":20},"pv":["$move"]}"""
        return MockResponse().setBody("""{"best":$item,"played":$item,"comparison":{"canCompare":$comparable,"commonDepth":22},"engine":{"name":"Stockfish","version":"19"}}""")
    }

    @Test fun inFlightReviewSurvivesAnotherHumanAndAiMoveAndTheSavedTourUsesItsCache() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val payloads = CopyOnWriteArrayList<JsonObject>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    payloads.add(Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject)
                    if (calls.incrementAndGet() == 1) check(release.await(25, TimeUnit.SECONDS))
                    return response(request)
                }
            }
            start()
        }
        val model = GameViewModel(app, RemoteStockfishClient({ "test-token" }, server.url("/").toString()))
        val store = ViewModelStore().apply { put("background", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            val game = GameRecord(moves = listOf("e2e4", "e7e5"), humanWhite = true, playerEloAtStart = 500, opponentElo = 500)
            model.load(game)
            model.saveSettings(PlaySettings(color = ColorPreference.WHITE, stockfishToken = "test-token"))
            model.page(0)
            waitFor(model) { calls.get() == 1 }
            assertTrue(model.state.value.analyzing)
            assertFalse(model.state.value.busy)
            model.play("g1f3")
            waitFor(model) { !model.state.value.busy && model.state.value.game.moves.size == 4 }
            assertEquals(1, calls.get())
            val moves = model.state.value.game.moves
            release.countDown()
            waitFor(model) { !model.state.value.analyzing && model.state.value.game.reviews.size == 4 }
            assertNull(model.state.value.error)
            assertEquals(moves, model.state.value.game.moves)
            assertEquals(4, calls.get())
            assertTrue(model.state.value.game.reviews.all { it.analysisProfile == "lightning" && it.canReuseDeep(500, "Stockfish 19") })
            for (payload in payloads) {
                assertEquals("lightning", payload.getValue("profile").jsonPrimitive.content)
                assertEquals(22, payload.getValue("limits").jsonObject.getValue("depth").jsonPrimitive.int)
                assertEquals(500, payload.getValue("limits").jsonObject.getValue("maxTimeMs").jsonPrimitive.int)
            }
            val saved = runBlocking { GameRepository(app).latest() }!!
            assertEquals(game.id, saved.id)
            assertEquals(moves, saved.moves)
            assertEquals(4, saved.reviews.size)
            model.load(saved.copy(finished = true, result = "1/2-1/2"))
            model.reviewHighlights()
            waitFor(model) { !model.state.value.busy }
            assertTrue(model.state.value.highlightsOpen)
            assertEquals(4, calls.get())
            assertNull(model.state.value.error)
            model.page(0)
            settle()
            assertEquals(4, calls.get())
            val timings = AnalysisTimings.snapshot().getJSONArray("analysis")
            assertEquals(4, timings.length())
            assertTrue((0 until timings.length()).all { timings.getJSONObject(it).getString("profile") == "lightning" })
        } finally { release.countDown(); store.clear(); server.shutdown() }
    }

    @Test fun incomparableResultsDoNotCreateAnEndlessBackgroundRetryLoop() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = response(request, comparable = false)
            }; start()
        }
        val model = GameViewModel(app, RemoteStockfishClient({ "test-token" }, server.url("/").toString()))
        val store = ViewModelStore().apply { put("bounded", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            model.load(GameRecord(moves = listOf("e2e4", "e7e5"), finished = true))
            model.saveSettings(PlaySettings(stockfishToken = "test-token"))
            model.page(0)
            waitFor(model) { !model.state.value.analyzing && model.state.value.game.reviews.size == 2 }
            settle()
            assertEquals(2, server.requestCount)
            assertTrue(model.state.value.game.reviews.all { it.grade == Grade.UNSTABLE && it.provisional })
            assertNull(model.state.value.error)
        } finally { store.clear(); server.shutdown() }
    }

    @Test fun switchingToDeepRefreshesLightningCacheWithoutLosingTheGameAndManualReevaluationStaysDeep() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val profiles = CopyOnWriteArrayList<String>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val payload = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                    profiles.add(payload.getValue("profile").jsonPrimitive.content)
                    if (profiles.last() == "deep") assertTrue(payload["limits"] == null || payload["limits"] == JsonNull)
                    return response(request)
                }
            }; start()
        }
        val model = GameViewModel(app, RemoteStockfishClient({ "test-token" }, server.url("/").toString()))
        val store = ViewModelStore().apply { put("budget", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            val game = GameRecord(moves = listOf("e2e4", "e7e5"), finished = true)
            model.load(game)
            val settings = PlaySettings(stockfishToken = "test-token")
            model.saveSettings(settings)
            model.page(0)
            waitFor(model) { !model.state.value.analyzing && model.state.value.game.reviews.size == 2 }
            model.saveSettings(settings.copy(analysisBudget = AnalysisBudget.DEEP))
            waitFor(model) { !model.state.value.analyzing && model.state.value.game.reviews.all { it.analysisProfile == "deep" } }
            assertEquals(listOf("lightning", "lightning", "deep", "deep"), profiles.toList())
            model.saveSettings(settings)
            settle()
            assertEquals(4, server.requestCount)
            assertEquals(game.id, model.state.value.game.id)
            assertEquals(game.moves, model.state.value.game.moves)
            model.page(1)
            model.analyzeSelected()
            waitFor(model) { !model.state.value.busy && server.requestCount == 5 }
            assertEquals("deep", profiles.last())
            assertNull(model.state.value.error)
        } finally { store.clear(); server.shutdown() }
    }

    @Test fun lazilyOpenedRoomDatabaseIsStillASingleton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val first = GameDatabase.get(app)
        assertFalse(first.isOpen)
        assertSame(first, GameDatabase.get(app))
    }
}
