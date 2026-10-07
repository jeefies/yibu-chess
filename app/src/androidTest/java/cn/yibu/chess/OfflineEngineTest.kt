package cn.yibu.chess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.MoveAnalyzer
import cn.yibu.chess.core.SearchRequest
import cn.yibu.chess.engine.NativeStockfish
import cn.yibu.chess.engine.MaiaModel
import cn.yibu.chess.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineEngineTest {
    @Test fun bundledHumanModelRunsOfflineForBothColorsAndSpecialMoves() = runBlocking {
        val model = MaiaModel(InstrumentationRegistry.getInstrumentation().targetContext)
        model.initialize()
        val positions = listOf(emptyList(), listOf("e2e4"),
            listOf("e2e4", "a7a6", "e4e5", "d7d5"),
            "a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2 b7a8q".split(" "))
        for (history in positions) {
            val logits = model.logits(history, 1000, 1000)
            val candidates = HumanSampling.candidates(ChessRules.legal(history), logits, history.size % 2 == 0)
            assertEquals(1.0, candidates.sumOf { it.probability }, 1e-9)
            assertTrue(candidates.all { it.probability.isFinite() && it.move in ChessRules.legal(history) })
        }
    }
    @Test fun bundledNetworksLoadAndReturnLegalRatedMoves() = runBlocking {
        val engine = NativeStockfish(InstrumentationRegistry.getInstrumentation().targetContext)
        engine.initialize()
        val result = engine.search(emptyList(), SearchRequest(timeMs = 400, multiPv = 3))
        assertTrue(result.bestMove in ChessRules.legal(emptyList()))
        assertEquals(3, result.lines.size)
        assertEquals(1000, result.best.win + result.best.draw + result.best.loss)
        val review = MoveAnalyzer(engine).analyze(emptyList(), "e2e4", false)
        assertEquals(1, review.ply)
        assertTrue(review.bestMove in ChessRules.legal(emptyList()))
        assertEquals(review.best.depth, review.played.depth)
    }
}
