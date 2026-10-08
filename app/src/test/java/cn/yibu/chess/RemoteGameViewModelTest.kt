package cn.yibu.chess

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import cn.yibu.chess.data.PlayPreferences
import cn.yibu.chess.engine.RemoteStockfishClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteGameViewModelTest {
    private fun waitFor(model: GameViewModel, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            Thread.sleep(20)
        }
        assertTrue("State did not settle: ${model.state.value.status}, ${model.state.value.error}", condition())
    }
    @Test fun offlineMaiaCanPlayTwoPlayerMovesAndMissingTokenDoesNotReplaceStrongestOpponent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val model = GameViewModel(app)
        val store = ViewModelStore().apply { put("remote", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            model.newGame(Difficulty.MATCHED, true)
            waitFor(model) { !model.state.value.busy && model.state.value.game.moves.isEmpty() }
            val id = model.state.value.game.id
            model.newGame(Difficulty.STRONG, true)
            assertEquals(id, model.state.value.game.id)
            assertTrue(model.state.value.error.orEmpty().contains("Access Token"))
            model.clearError()
            model.play("e2e4")
            waitFor(model) { !model.state.value.busy && model.state.value.game.moves.size == 2 }
            val beforeSecond = model.state.value.game.moves
            model.play(ChessRules.legal(beforeSecond).first())
            waitFor(model) { !model.state.value.busy && model.state.value.game.moves.size == 4 }
            assertNull(model.state.value.error)
            assertTrue(model.state.value.game.reviews.isEmpty())
            assertEquals("Maia-3 5M", model.state.value.game.opponentEngine)
        } finally { store.clear() }
    }

    @Test fun savingTokenKeepsTheGameAndNavigationCancelsRemoteReview() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PlayPreferences(app).save(PlaySettings(color = ColorPreference.WHITE))
        val server = MockWebServer().apply { start() }
        val remote = RemoteStockfishClient({ PlayPreferences(app).read().stockfishToken }, server.url("/").toString())
        val model = GameViewModel(app, remote)
        val store = ViewModelStore().apply { put("remote", model) }
        try {
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            val game = GameRecord(moves = listOf("e2e4", "e7e5"), humanWhite = true)
            model.load(game)
            model.saveSettings(PlaySettings(color = ColorPreference.WHITE, stockfishToken = " saved-token "))
            assertEquals(game.id, model.state.value.game.id)
            assertEquals(game.moves, model.state.value.game.moves)
            assertEquals(1, model.state.value.page)
            assertEquals("saved-token", PlayPreferences(app).read().stockfishToken)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            model.analyzeSelected()
            waitFor(model) { server.requestCount == 1 }
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/sf/v1/analyze-move", request.path)
            model.page(2)
            waitFor(model) { !model.state.value.busy }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(2, model.state.value.page)
            assertEquals(game.moves, model.state.value.game.moves)
            assertTrue(model.state.value.game.reviews.isEmpty())
            assertNull(model.state.value.error)
            // Saved remote analysis can still drive the manual tour without a token.
            val cached = game.moves.mapIndexed { i, move ->
                val evaluation = Evaluation(22, cp = 20, pv = listOf(move))
                MoveReview(i + 1, move, ChessRules.san(game.moves.take(i), move), evaluation, evaluation,
                    grade = Grade.BEST, explanation = "", provisional = false,
                    algorithmVersion = 3, engineVersion = "Stockfish 19", deeplySearched = true)
            }
            model.load(game.copy(reviews = cached))
            model.saveSettings(PlaySettings(color = ColorPreference.WHITE))
            model.reviewHighlights()
            waitFor(model) { !model.state.value.busy && model.state.value.highlightsOpen }
            assertNull(model.state.value.error)
            assertEquals(1, server.requestCount)
        } finally { store.clear(); server.shutdown() }
    }
}
