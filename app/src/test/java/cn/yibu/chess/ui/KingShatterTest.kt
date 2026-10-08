package cn.yibu.chess.ui

import android.animation.ValueAnimator
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.KingBreak
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KingShatterTest {
    @get:Rule val compose = createComposeRule()
    private fun scale(value: Float) { ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, value) }
    private fun advance(ms: Int) { repeat((ms + 15) / 16) {
        shadowOf(Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle()
    } }

    @Test fun bothKingsHaveMovingShardsAndConsumedEventsDoNotReplayAfterFlipping() {
        scale(1f)
        val effect = mutableStateOf<KingBreak?>(null)
        val flip = mutableStateOf(false)
        val completed = mutableListOf<Long>()
        val started = mutableListOf<Long>()
        compose.setContent { ChessBoard(ChessRules.START_FEN, flip.value, null, emptySet(), null,
            kingBreak = effect.value, onKingBreakFinished = { completed += it; effect.value = null }, onKingBreakStarted = { started += it }) {} }
        compose.mainClock.autoAdvance = false
        try {
            listOf(KingBreak(100, 4, true, true), KingBreak(101, 60, false, false)).forEachIndexed { index, event ->
                compose.runOnIdle { flip.value = index == 1; effect.value = event }
                advance(32)
                if (event.checkmate) {
                    compose.onNodeWithTag("king-shard-0").assertDoesNotExist()
                    assertFalse(started.contains(event.gameId))
                    advance(280)
                }
                compose.onNodeWithTag("king-shard-0", useUnmergedTree = true).assertExists()
                val start = compose.onNodeWithTag("king-shard-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
                advance(160)
                val moving = compose.onNodeWithTag("king-shard-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertNotEquals("The shards must actually move", start, moving)
                if (index == 0) compose.featureScreenshot("king-shatter-mid-frame")
                assertEquals(0, completed.count { it == event.gameId })
                assertEquals(1, started.count { it == event.gameId })
                advance(900)
                compose.onNodeWithTag("king-shard-0").assertDoesNotExist()
                assertEquals(1, completed.count { it == event.gameId })
                compose.runOnIdle { flip.value = !flip.value }
                advance(64)
                compose.onNodeWithTag("king-shard-0").assertDoesNotExist()
            }
        } finally { compose.mainClock.autoAdvance = true; scale(1f) }
    }

    @Test fun disabledAnimationsConsumeTheEventWithoutFlyingPiecesOrDelay() {
        val effect = mutableStateOf<KingBreak?>(null)
        var completed = 0
        var started = 0
        compose.setContent { ChessBoard(ChessRules.START_FEN, false, null, emptySet(), null,
            kingBreak = effect.value, onKingBreakFinished = { completed++; effect.value = null }, onKingBreakStarted = { started++ }) {} }
        try {
            scale(0f); compose.mainClock.autoAdvance = false
            compose.runOnIdle { effect.value = KingBreak(102, 4, true, true) }
            advance(64)
            assertEquals(1, completed)
            assertEquals(0, started)
            compose.onNodeWithTag("king-shard-0").assertDoesNotExist()
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
        } finally { scale(1f); compose.mainClock.autoAdvance = true }
    }
}
