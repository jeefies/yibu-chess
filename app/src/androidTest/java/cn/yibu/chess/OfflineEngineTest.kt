package cn.yibu.chess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.MoveAnalyzer
import cn.yibu.chess.core.SearchRequest
import cn.yibu.chess.engine.NativeStockfish
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineEngineTest {
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
