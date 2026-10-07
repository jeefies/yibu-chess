package cn.yibu.chess.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.yibu.chess.core.ChessRules
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChessBoardTest {
    @get:Rule val compose = createComposeRule()

    private fun tap(square: String, flipped: Boolean) {
        val index = ChessRules.squareIndex(square)
        val col = if (flipped) 7 - index % 8 else index % 8
        val row = if (flipped) index / 8 else 7 - index / 8
        compose.onNodeWithContentDescription("国际象棋棋盘，${if (flipped) "黑方" else "白方"}视角")
            .performTouchInput { click(Offset((col + .5f) * width / 8f, (row + .5f) * height / 8f)) }
    }

    private fun turns(flipped: Boolean) {
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5")
        val history = mutableStateOf(moves.take(if (flipped) 1 else 0))
        val busy = mutableStateOf(false)
        val received = mutableListOf<Int>()
        compose.setContent {
            val acceptingInput = !busy.value
            ChessBoard(ChessRules.board(history.value).fen, flipped, null, emptySet(), history.value.lastOrNull()) { square ->
                if (acceptingInput) received.add(square)
            }
        }
        tap(if (flipped) "e7" else "e2", flipped)
        compose.runOnIdle { assertEquals(1, received.size) }
        repeat(2) { turn ->
            // The AI's new position appears while analysis still marks the turn busy.
            compose.runOnIdle { busy.value = true; history.value = moves.take((turn + 1) * 2 + if (flipped) 1 else 0) }
            val square = if (flipped) listOf("b8", "f8")[turn] else listOf("g1", "f1")[turn]
            tap(square, flipped)
            compose.runOnIdle { assertEquals(turn + 1, received.size) }
            // Unlocking changes the callback, but does not change the FEN.
            compose.runOnIdle { busy.value = false }
            tap(square, flipped)
            compose.runOnIdle {
                assertEquals("Turn ${turn + 2} must accept a tap without flipping or changing position", turn + 2, received.size)
                assertEquals(ChessRules.squareIndex(square), received.last())
            }
        }
    }

    @Test fun whiteCanTapOnSecondAndThirdTurns() = turns(false)
    @Test fun blackCanTapOnSecondAndThirdTurns() = turns(true)

    @Test fun readinessCanUnlockTheSameInitialPosition() {
        val ready = mutableStateOf(false)
        val received = mutableListOf<Int>()
        compose.setContent {
            val acceptingInput = ready.value
            ChessBoard(ChessRules.board(emptyList()).fen, false, null, emptySet(), null) { square ->
                if (acceptingInput) received.add(square)
            }
        }
        tap("e2", false)
        compose.runOnIdle { assertTrue(received.isEmpty()); ready.value = true }
        tap("e2", false)
        compose.runOnIdle { assertEquals(listOf(ChessRules.squareIndex("e2")), received) }
    }
}
