package cn.yibu.chess.ui

import android.animation.ValueAnimator
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.yibu.chess.core.ChessRules
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChessMotionTest {
    @get:Rule val compose = createComposeRule()
    private fun frame() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }
    private fun advance(milliseconds: Int) { repeat((milliseconds + 15) / 16) { frame() } }
    private fun screenshot(name: String) {
        compose.runOnIdle {
            val root = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("../artifacts/ui-0.5.0/$name.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun setScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @Test fun slideHasAnIntermediatePositionAndReversalStartsAtTheCurrentPosition() {
        setScale(1f)
        assertTrue(ValueAnimator.areAnimatorsEnabled())
        val history = mutableStateOf(emptyList<String>())
        compose.setContent { ChessBoard(ChessRules.board(history.value).fen, false, null, emptySet(), null) {} }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { history.value = listOf("e2e4") }
        frame(); frame()
        val board = compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").getUnclippedBoundsInRoot()
        val cell = (board.right.value - board.left.value) / 8
        val start = compose.onNodeWithTag("piece-motion-e4", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value + 4.5f * cell
        advance(96)
        val middle = compose.onNodeWithTag("piece-motion-e4", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value + 4.5f * cell
        assertTrue("Piece must move towards its target: start=$start, middle=$middle", middle < start)
        assertTrue("The intermediate frame must not teleport to the destination", middle > board.top.value + 4.5f * cell)
        screenshot("piece-mid-slide")
        compose.runOnIdle { history.value = emptyList() }
        frame()
        val reverseStart = compose.onNodeWithTag("piece-motion-e2", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value + 6.5f * cell
        // The next frame may advance the old flight before cancellation. At this
        // curve's peak speed, a 16ms frame can cover about 0.64 of a square.
        assertTrue("Reversal must stay between the original endpoints: $reverseStart", reverseStart > board.top.value + 4.5f * cell && reverseStart < board.top.value + 6.5f * cell)
        assertTrue("Reversal must continue from the current flight: before=$middle, after=$reverseStart", abs(reverseStart - middle) < cell * .75f)
        advance(320)
        compose.onAllNodes(hasTestTag("piece-motion-e2")).assertCountEquals(0)
        compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun castlingAnimatesBothPiecesAndANewGameOrFlipCancelsTheOldScene() {
        setScale(1f)
        val opening = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        val history = mutableStateOf(opening)
        val key = mutableStateOf(1L)
        val flipped = mutableStateOf(false)
        compose.setContent { ChessBoard(ChessRules.board(history.value).fen, flipped.value, null, emptySet(), null, animationKey = key.value) {} }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { history.value = opening + "e1g1" }
        frame(); frame()
        compose.onNodeWithTag("piece-motion-g1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("piece-motion-f1", useUnmergedTree = true).assertExists()
        compose.runOnIdle { key.value++ }
        frame()
        compose.onNodeWithTag("piece-motion-g1").assertDoesNotExist()
        compose.onNodeWithTag("piece-motion-f1").assertDoesNotExist()
        compose.runOnIdle { history.value = opening }
        frame(); frame()
        compose.onNodeWithTag("piece-motion-e1", useUnmergedTree = true).assertExists()
        compose.runOnIdle { flipped.value = true }
        frame()
        compose.onNodeWithTag("piece-motion-e1").assertDoesNotExist()
        compose.onNodeWithContentDescription("国际象棋棋盘，黑方视角").assertIsDisplayed()
        // After a nonadjacent review jump, the next adjacent step must still animate.
        compose.runOnIdle { history.value = emptyList() }
        frame(); frame()
        compose.runOnIdle { history.value = listOf("e2e4") }
        frame(); frame()
        compose.onNodeWithTag("piece-motion-e4", useUnmergedTree = true).assertExists()
        advance(320)
        compose.mainClock.autoAdvance = true
    }

    @Test fun disabledSystemAnimationsSnapToTheNewPosition() {
        val history = mutableStateOf(emptyList<String>())
        compose.setContent { ChessBoard(ChessRules.board(history.value).fen, false, null, emptySet(), null) {} }
        try {
            setScale(0f)
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { history.value = listOf("e2e4") }
            frame(); frame()
            compose.onNodeWithTag("piece-motion-e4").assertDoesNotExist()
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
        } finally { setScale(1f); compose.mainClock.autoAdvance = true }
    }
}
