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
        assertEquals(emptyList<String>(), model.state.value.game.moves)
        assertTrue("Startup should reach engine-ready or a visible startup error", model.state.value.ready || model.state.value.error != null)
        if (System.getProperty("startup.native") == "true") {
            assertTrue("Bundled engine should initialize: ${model.state.value.error}", model.state.value.ready)
            model.newGame(Difficulty.RELAXED, true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            model.play("e2e4")
            waitFor(model) { model.state.value.game.moves.size == 2 && !model.state.value.busy }
            assertEquals(null, model.state.value.error)
            assertEquals(2, model.state.value.game.reviews.size)
            assertEquals(20, ChessRules.legal(emptyList()).size)
            ChessRules.board(model.state.value.game.moves)
        }
        controller.pause().stop().destroy()
    }
}
