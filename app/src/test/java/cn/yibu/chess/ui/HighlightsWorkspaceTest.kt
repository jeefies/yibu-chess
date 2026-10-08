package cn.yibu.chess.ui

import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.yibu.chess.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightsWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private fun advance(ms: Int) { repeat((ms + 15) / 16) {
        shadowOf(Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle()
    } }
    @Test fun tourAutoplaysActualAndRecommendedMovesPausesChangesPointsAndCompletesOnSmallScreen() {
        val game = GameRecord(moves = listOf("e2e4", "e7e5", "g1f3"))
        fun lesson(ply: Int, line: List<String>): MoveLesson {
            val root = game.moves.take(ply - 1)
            val ev = Evaluation(22, cp = 20, pv = line)
            return MoveCoach.explain(root, MoveReview(ply, game.moves[ply - 1], ChessRules.san(root, game.moves[ply - 1]),
                ev, ev, grade = Grade.GOOD, explanation = ""))
        }
        val highlights = listOf(ReviewHighlight(1, "值得改进的一步", "先看看实战 e4", lesson(1, listOf("d2d4", "d7d5", "c2c4"))),
            ReviewHighlight(3, "阶段回顾", "马的发展", lesson(3, listOf("g1f3", "b8c6"))))
        var closed = false
        compose.setContent { ChessTheme { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(70.dp))
            Box(Modifier.weight(1f)) { HighlightsWorkspace(game, highlights, false, { closed = true }, {}) }
            Spacer(Modifier.height(80.dp))
        } } }
        compose.mainClock.autoAdvance = false
        try {
            advance(2000)
            compose.onNodeWithTag("highlight-position").assertTextEquals("实战 · 白方 e4")
            compose.onNodeWithText("暂停").assertIsDisplayed().performClick()
            advance(6000)
            compose.onNodeWithTag("highlight-position").assertTextEquals("实战 · 白方 e4")
            compose.onNodeWithText("继续播放").performClick()
            advance(3500)
            compose.onNodeWithTag("highlight-position").assertTextEquals("回到起点 · 看推荐走法")
            compose.onNodeWithTag("highlight-explanation").assertTextContains("建议白方走 d4", substring = true)
            advance(1900)
            compose.onNodeWithTag("highlight-position").assertTextEquals("推荐路线 1 / 3")
            compose.onNodeWithTag("highlight-explanation").assertTextContains("白方 d4", substring = true)
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
            compose.featureScreenshot("recommended-line-small-screen")
            compose.onNodeWithText("下个点").assertIsDisplayed().performClick()
            advance(6000)
            compose.onNodeWithTag("highlight-position").assertTextEquals("第 3 步之前")
            compose.onNodeWithTag("highlight-title").assertTextContains("2 / 2", substring = true)
            compose.onNodeWithText("继续播放").performClick()
            advance(14500)
            compose.onNodeWithText("复盘完成").assertIsDisplayed()
            compose.onNodeWithText("再看一次").performClick()
            advance(64)
            compose.onNodeWithTag("highlight-title").assertTextContains("1 / 2", substring = true)
            compose.onNodeWithText("逐步复盘").performClick()
            assertTrue(closed)
            assertEquals(listOf("e2e4", "e7e5", "g1f3"), game.moves)
            assertTrue(game.lessons.isEmpty())
        } finally { compose.mainClock.autoAdvance = true }
    }
}
