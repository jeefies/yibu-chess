package cn.yibu.chess.ui

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.GameViewModel
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.Difficulty
import cn.yibu.chess.core.GameRecord
import cn.yibu.chess.data.GameRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Exercises actual taps, actual Maia inference and the same Stockfish JNI source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun lessonScreenshot(name: String) {
        compose.runOnIdle {
            val root = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            val output = java.io.File("../artifacts/ui-0.6.0").apply { mkdirs() }
            java.io.File(output, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun deepReviewCacheOpensAnAnnotatedBoardAndPlaybackWithoutRepeatingSearch() {
        assumeTrue(System.getProperty("startup.native") == "true")
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("lesson", model) }
        try {
            compose.setContent { ChessApp(model) }
            waitFor(model) { model.state.value.ready && !model.state.value.busy }
            val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6")
            val reviews = moves.mapIndexed { index, move ->
                // A search capped at depth 22 cannot produce these depth-26 sentinel results.
                val pv = if (index == 2) listOf("g1f3", "b8c6", "f1b5", "a7a6") else listOf(move)
                val evaluation = cn.yibu.chess.core.Evaluation(26, cp = 12, pv = pv)
                cn.yibu.chess.core.MoveReview(index + 1, move, ChessRules.san(moves.take(index), move), evaluation, evaluation,
                    grade = cn.yibu.chess.core.Grade.GOOD, explanation = "", provisional = true,
                    algorithmVersion = 3, scoringElo = 500, deeplySearched = true)
            }
            val game = cn.yibu.chess.core.EloRules.newGame(cn.yibu.chess.core.PlayerProfile(), humanWhite = true)
                .copy(moves = moves, reviews = reviews)
            compose.runOnIdle { model.load(game); model.reviewAll() }
            waitFor(model) { !model.state.value.busy }
            assertEquals(reviews, model.state.value.game.reviews)
            compose.runOnIdle { model.cursor(3); model.explainSelected() }
            waitFor(model) { !model.state.value.busy }
            assertNull(model.state.value.error)
            assertEquals(reviews, model.state.value.game.reviews)
            assertTrue(model.state.value.lessonOpen)
            val lesson = model.state.value.chosenLesson!!
            assertEquals(lesson.variation, lesson.steps.map { it.uci })
            compose.onNodeWithTag("lesson-why").assertTextEquals(lesson.why).assertIsDisplayed()
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
            lessonScreenshot("lesson-why")
            compose.onNodeWithText("下一步").performClick()
            assertEquals(moves.take(2) + lesson.variation.take(1), model.state.value.boardHistory)
            compose.onNodeWithTag("lesson-step-explanation").assertTextEquals(lesson.steps.first().explanation).assertIsDisplayed()
            lessonScreenshot("lesson-step")
            compose.onNodeWithText("演示").performClick()
            waitFor(model) { model.state.value.variationStep >= 2 }
            compose.onNodeWithText("暂停演示").performClick()
            val paused = model.state.value.variationStep
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
            assertEquals(paused, model.state.value.variationStep)
            compose.onNodeWithText("全文").performClick()
            compose.onNodeWithTag("lesson-plan").assertTextEquals(lesson.plan)
            compose.onNodeWithContentDescription("国际象棋棋盘，白方视角").assertIsDisplayed()
            compose.onNodeWithText("返回复盘").performClick()
            assertFalse(model.state.value.lessonOpen)
            assertEquals(3, model.state.value.cursor)
            compose.runOnIdle { model.explainSelected() }
            assertFalse(model.state.value.busy)
            assertTrue(model.state.value.lessonOpen)
            val restored = runBlocking { GameRepository(ApplicationProvider.getApplicationContext()).latest() }
            assertEquals(lesson, restored?.lessons?.single())
        } finally { compose.runOnIdle { store.clear() } }
    }

    private fun waitFor(model: GameViewModel, condition: () -> Boolean) {
        try {
            compose.waitUntil(45_000) {
                // Compose's frame clock does not drain Android Handler continuations.
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
                condition()
            }
        } catch (e: ComposeTimeoutException) {
            val state = model.state.value
            throw AssertionError("Timed out: ready=${state.ready}, busy=${state.busy}, analyzing=${state.analyzing}, status=${state.status}, moves=${state.game.moves}, error=${state.error}", e)
        }
    }

    private fun tap(square: String, humanWhite: Boolean) {
        val index = ChessRules.squareIndex(square)
        val col = if (humanWhite) index % 8 else 7 - index % 8
        val row = if (humanWhite) 7 - index / 8 else index / 8
        compose.onNodeWithContentDescription("国际象棋棋盘，${if (humanWhite) "白方" else "黑方"}视角")
            .performScrollTo()
            .performTouchInput { click(Offset((col + .5f) * width / 8f, (row + .5f) * height / 8f)) }
    }

    private fun play(humanWhite: Boolean, difficulty: Difficulty, turns: Int) {
        assumeTrue("Run with -PstartupNativeDir pointing to the host JNI build", System.getProperty("startup.native") == "true")
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("game", model) }
        try {
            compose.setContent { ChessApp(model) }
            waitFor(model) { model.state.value.ready || model.state.value.error != null }
            assertTrue("Engine startup failed: ${model.state.value.error}", model.state.value.ready)
            compose.runOnIdle { model.newGame(difficulty, humanWhite) }
            waitFor(model) { !model.state.value.transitioning && !model.state.value.busy && model.state.value.humanTurn }
            var playedDuringAnalysis = false
            val preferred = if (humanWhite) listOf("e2e4", "g1f3", "f1c4") else listOf("e7e5", "b8c6", "f8c5")
            repeat(turns) { turn ->
                val before = model.state.value
                assertEquals(humanWhite, before.game.humanWhite)
                assertFalse(before.busy)
                if (before.analyzing) playedDuringAnalysis = true
                val legal = ChessRules.legal(before.game.moves)
                val move = preferred[turn].takeIf { it in legal } ?: legal.first()
                tap(move.take(2), humanWhite)
                tap(move.substring(2, 4), humanWhite)
                compose.runOnIdle {
                    assertTrue("Turn ${turn + 1} must accept the move from the board", model.state.value.game.moves.size > before.game.moves.size)
                    assertEquals(move, model.state.value.game.moves[before.game.moves.size])
                }
                waitFor(model) { model.state.value.game.moves.size == before.game.moves.size + 2 && !model.state.value.busy }
                assertTrue(model.state.value.humanTurn)
                assertNull(model.state.value.error)
            }
            assertTrue("At least one next turn should be playable while analysis is active", playedDuringAnalysis)

            // Navigation stops live work, and returning resumes the missing analyses.
            compose.runOnIdle { model.page(1) }
            assertFalse(model.state.value.analyzing)
            val history = model.state.value.game.moves
            compose.runOnIdle { model.page(0); model.pauseForBackground() }
            assertFalse(model.state.value.analyzing)
            assertEquals(history, model.state.value.game.moves)
            compose.runOnIdle { model.resumeForeground() }
            waitFor(model) { !model.state.value.analyzing && model.state.value.game.reviews.size == history.size }
            assertEquals((1..history.size).toList(), model.state.value.game.reviews.map { it.ply })
            assertEquals(history, model.state.value.game.moves)
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.error)
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test fun whiteCanPlayThreeTurnsWhileMaiaAndStockfishRun() = play(true, Difficulty.MATCHED, 3)
    @Test fun blackCanPlayThreeTurnsWhileMaiaAndStockfishRun() = play(false, Difficulty.MATCHED, 3)
    @Test fun strongOpponentTakesPriorityOverBackgroundAnalysis() = play(true, Difficulty.STRONG, 2)

    @Test fun thinkingPauseDoesNotCommitAMoveAfterNavigationBackgroundOrNewGame() {
        assumeTrue(System.getProperty("startup.native") == "true")
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("game", model) }
        try {
            compose.setContent { ChessApp(model) }
            waitFor(model) { model.state.value.ready }
            compose.runOnIdle { model.newGame(Difficulty.MATCHED, true) }
            waitFor(model) { !model.state.value.transitioning && !model.state.value.busy }
            compose.runOnIdle { model.play("e2e4") }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(listOf("e2e4"), model.state.value.game.moves)
            assertTrue(model.state.value.busy)
            compose.runOnIdle { model.page(1) }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertEquals(listOf("e2e4"), model.state.value.game.moves)
            assertFalse(model.state.value.busy)
            compose.runOnIdle { model.page(0); model.pauseForBackground() }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertEquals(listOf("e2e4"), model.state.value.game.moves)
            val previousId = model.state.value.game.id
            compose.runOnIdle { model.resumeForeground(); model.newGame(Difficulty.MATCHED, true) }
            waitFor(model) { !model.state.value.transitioning && model.state.value.game.id != previousId }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertTrue(model.state.value.game.moves.isEmpty())
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.error)
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun explainingOneStepWhileChangingCursorSavesOnlyTheRequestedStep() {
        assumeTrue(System.getProperty("startup.native") == "true")
        val model = GameViewModel(ApplicationProvider.getApplicationContext())
        val store = ViewModelStore().apply { put("game", model) }
        try {
            compose.setContent { ChessApp(model) }
            waitFor(model) { model.state.value.ready }
            val game = GameRecord(moves = listOf("e2e4", "e7e5", "g1f3", "b8c6"))
            compose.runOnIdle { model.load(game); model.cursor(3); model.explainSelected(); model.cursor(1) }
            waitFor(model) { !model.state.value.busy }
            assertNull(model.state.value.error)
            assertEquals(1, model.state.value.cursor)
            assertEquals(listOf(3), model.state.value.game.lessons.map { it.ply })
            assertEquals(listOf(3), model.state.value.game.reviews.map { it.ply })
            assertNull(model.state.value.chosenLesson)
            val lesson = model.state.value.game.lessons.single()
            assertTrue(lesson.why.isNotBlank() && lesson.plan.contains("对手关键应对"))
            assertEquals(lesson.variation, ChessRules.legalVariation(game.moves.take(2), lesson.variation))
            val restored = runBlocking { GameRepository(ApplicationProvider.getApplicationContext()).latest() }
            assertEquals(listOf(lesson), restored?.lessons)
            compose.runOnIdle { model.cursor(3); model.explainSelected() }
            assertFalse("Cached explanations should not start another search", model.state.value.busy)
            assertEquals(lesson, model.state.value.chosenLesson)
            compose.runOnIdle { model.showLessonVariation() }
            assertEquals(2, model.state.value.variationBase)
            assertEquals(lesson.variation, model.state.value.variation)
        } finally { compose.runOnIdle { store.clear() } }
    }
}
