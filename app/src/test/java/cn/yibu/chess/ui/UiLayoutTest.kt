package cn.yibu.chess.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.AppState
import cn.yibu.chess.GameViewModel
import cn.yibu.chess.core.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Render actual Compose screens and verify that essential actions survive mobile widths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6")
    private val base = EloRules.newGame(PlayerProfile(), humanWhite = true).copy(
        moves = moves, opponentEngine = "Maia-3 5M",
        reviews = moves.mapIndexed { index, move ->
            val evaluation = Evaluation(18, cp = listOf(30, -35, 22, -41, 30, -55, 27, -21)[index], pv = listOf(move))
            MoveReview(index + 1, move, ChessRules.san(moves.take(index), move), evaluation, evaluation,
                grade = Grade.BEST, explanation = "发展子力，保持中心的主动权。", provisional = false, algorithmVersion = 3)
        })

    private fun screenshot(name: String) {
        val directory = File("../artifacts/ui-0.5.0").apply { mkdirs() }
        compose.runOnIdle {
            // PixelCopy needs hardware vsync, absent in Robolectric. Draw the actual Android
            // root into a native Skia canvas instead; the last window also captures dialogs.
            val root = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun phoneScreensKeepBoardAndNavigationVisible() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("ui", model) }
        val state = mutableStateOf(AppState(game = base, ready = true, cursor = moves.size))
        try {
            compose.setContent { ChessScreen(state.value, model) }
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
            compose.onNodeWithContentDescription("对局设置").assertIsDisplayed()
            compose.onNodeWithText("轮到你走棋").assertIsDisplayed()
            compose.onNodeWithText("新局").assertIsDisplayed()
            screenshot("play")
            compose.runOnIdle { state.value = state.value.copy(game = base.copy(humanWhite = false)) }
            compose.onNodeWithContentDescription("国际象棋棋盘，黑方视角").assertIsDisplayed()
            screenshot("play-black")
            compose.runOnIdle { state.value = state.value.copy(page = 1, game = base) }
            screenshot("review")
            compose.onNodeWithText("下一步").assertIsDisplayed()
            compose.onNodeWithText("跟走推荐").performScrollTo().assertIsDisplayed()
            screenshot("review-analysis")
            compose.onNodeWithText("讲解这一步").performScrollTo().assertIsDisplayed()
            val root = moves.take(7)
            val line = listOf("g8f6", "e1g1", "f8e7", "f1e1", "b7b5", "a4b3")
            val evaluation = Evaluation(22, cp = -20, pv = line)
            val lesson = MoveCoach.explain(root, base.reviews.last().copy(best = evaluation))
            compose.runOnIdle { state.value = state.value.copy(game = base.copy(lessons = listOf(lesson),
                reviews = base.reviews.dropLast(1) + base.reviews.last().copy(best = evaluation, played = evaluation))) }
            compose.onNodeWithText("为什么这样走").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(lesson.why).performScrollTo().assertIsDisplayed()
            screenshot("review-lesson-why")
            compose.onNodeWithText("后续思路").performScrollTo().assertIsDisplayed()
            // A long text block can be taller than the viewport; scroll the parent
            // once instead of asking performScrollTo to fit the whole paragraph.
            compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("后续思路")))
                .performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 300f) }
            compose.onNodeWithText(lesson.plan).assertIsDisplayed()
            screenshot("review-lesson-plan")
            compose.onNodeWithText("跟走这条思路").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { state.value = state.value.copy(page = 2, games = listOf(base,
                base.copy(id = base.id + 1, difficulty = Difficulty.STRONG, rated = false, opponentEngine = "Stockfish 17.1",
                    finished = true, result = "0-1", humanWhite = true))) }
            compose.onAllNodesWithText("删除").assertCountEquals(2)
            screenshot("library")
            compose.runOnIdle { state.value = state.value.copy(games = emptyList()) }
            compose.onNodeWithText("开始一盘").assertIsDisplayed()
            screenshot("library-empty")
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun smallPhoneCanReachBoardSettingsAndLongReview() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("ui", model) }
        val state = mutableStateOf(AppState(game = base, ready = true, cursor = moves.size))
        try {
            compose.setContent { ChessScreen(state.value, model) }
            compose.onNodeWithText("新局").assertIsDisplayed()
            compose.onNodeWithContentDescription("对局设置").performClick()
            compose.onNodeWithText("随机").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithText("黑方").assertIsDisplayed()
            screenshot("settings-small")
            compose.onNodeWithText("取消").performClick()
            val board = compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").performScrollTo().fetchSemanticsNode().boundsInRoot
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("The complete board must fit when scrolled into view", board.top >= screen.top && board.bottom <= screen.bottom)
            screenshot("play-small")
            compose.runOnIdle {
                state.value = state.value.copy(page = 1, game = base.copy(reviews = base.reviews.map {
                    it.copy(explanation = "发展子力，注意保护王的安全；如果中心打开，需要协调车与象。".repeat(12))
                }))
            }
            compose.onNodeWithText("跟走推荐").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("整盘深度复评").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("导出 PGN").performScrollTo().assertIsDisplayed()
            screenshot("review-small-long-text")
        } finally { compose.runOnIdle { store.clear() } }
    }
}
