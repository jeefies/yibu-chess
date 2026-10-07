package cn.yibu.chess

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import cn.yibu.chess.core.Difficulty
import cn.yibu.chess.core.ChessRules
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupTest {
    private fun waitFor(model: GameViewModel, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(50)
        }
        assertTrue("Startup/playback timed out: ${model.state.value}", condition())
    }
    @Test fun launcherCreatesScreenAndReportsEngineStatus() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[GameViewModel::class.java]
        waitFor(model) { model.state.value.ready || model.state.value.error != null }
        assertTrue("Startup should reach engine-ready or a visible startup error", model.state.value.ready || model.state.value.error != null)
        if (System.getProperty("startup.native") == "true") {
            assertTrue("Bundled engine should initialize: ${model.state.value.error}", model.state.value.ready)
            waitFor(model) { !model.state.value.busy && model.state.value.humanTurn }
            assertEquals(if (model.state.value.game.humanWhite) 0 else 1, model.state.value.game.moves.size)
            assertEquals("Maia-3 5M", model.state.value.game.opponentEngine)
            model.newGame(Difficulty.MATCHED, true)
            waitFor(model) { !model.state.value.transitioning && model.state.value.game.rated }
            model.play("e2e4")
            waitFor(model) { model.state.value.game.moves.size == 2 && !model.state.value.busy }
            assertEquals(null, model.state.value.error)
            assertEquals(2, model.state.value.game.reviews.size)
            assertEquals(20, ChessRules.legal(emptyList()).size)
            ChessRules.board(model.state.value.game.moves)
            model.resign()
            waitFor(model) { model.state.value.game.ratingChange != null }
            assertEquals(468, model.state.value.profile.rating)
            assertEquals(1, model.state.value.profile.ratedGames)
            val finished = model.state.value.game
            model.page(2)
            model.load(finished)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(468, model.state.value.profile.rating)
            model.delete(finished)
            waitFor(model) { !model.state.value.transitioning && model.state.value.games.none { it.id == finished.id } }
            assertEquals(468, model.state.value.profile.rating)
            assertEquals(1, model.state.value.profile.ratedGames)
            model.newGame(Difficulty.STRONG, true)
            waitFor(model) { !model.state.value.transitioning && model.state.value.game.difficulty == Difficulty.STRONG }
            model.resign()
            waitFor(model) { model.state.value.games.any { it.id == model.state.value.game.id && it.finished } }
            assertEquals(468, model.state.value.profile.rating)
            assertEquals(null, model.state.value.game.ratingChange)
            assertEquals(468, model.state.value.game.playerEloAtStart)
            model.newGame(Difficulty.MATCHED, false)
            waitFor(model) { !model.state.value.transitioning && model.state.value.game.difficulty == Difficulty.MATCHED && model.state.value.game.moves.size == 1 && !model.state.value.busy }
            assertEquals(468, model.state.value.game.opponentElo)
            assertEquals(null, model.state.value.error)
            model.resign()
            model.delete(model.state.value.game)
            waitFor(model) { !model.state.value.transitioning && model.state.value.profile.ratedGames == 2 }
            assertEquals(436, model.state.value.profile.rating)
            model.configureAndStart(cn.yibu.chess.core.PlaySettings(color = cn.yibu.chess.core.ColorPreference.BLACK))
            waitFor(model) { !model.state.value.transitioning && !model.state.value.busy && model.state.value.game.moves.size == 1 }
            assertEquals(false, model.state.value.game.humanWhite)
            val prior = model.state.value.game.id
            model.newGame()
            waitFor(model) { model.state.value.game.id != prior && !model.state.value.transitioning && !model.state.value.busy && model.state.value.game.moves.size == 1 }
            assertEquals(false, model.state.value.game.humanWhite)
            assertEquals(cn.yibu.chess.core.ColorPreference.BLACK, model.state.value.settings.color)
        }
        controller.pause().stop().destroy()
    }
}
