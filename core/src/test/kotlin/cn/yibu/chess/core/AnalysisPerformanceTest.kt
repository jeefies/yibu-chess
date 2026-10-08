package cn.yibu.chess.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnalysisPerformanceTest {
    /** No shared depth forces the analyzer to repeat the root search as well as search the played move. */
    private class UnevenEngine : ChessEngine {
        val calls = mutableListOf<SearchRequest>()
        override fun stop() {}
        override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
            calls += request
            val move = request.restricted.firstOrNull() ?: "e2e4"
            val depth = if (request.restricted.isNotEmpty()) 16 else request.depth
            return SearchResult(move, mapOf(depth to listOf(Evaluation(depth, cp = 20, pv = listOf(move)))))
        }
    }

    @Test fun deepReviewKeepsExtraResourcesForPlayedMoveAndDepthRetry() = runBlocking {
        val engine = UnevenEngine()
        val review = MoveAnalyzer(engine).analyze(emptyList(), "a2a3", deep = true)
        assertEquals(3, engine.calls.size)
        assertTrue(engine.calls.all { it.threads == 6 && it.hashMb == 256 && it.timeMs == 6000 })
        assertEquals(22, engine.calls.first().depth)
        assertEquals(listOf("a2a3"), engine.calls[1].restricted)
        assertEquals(16, engine.calls.last().depth)
        assertEquals(review.best.depth, review.played.depth)
    }

    @Test fun ordinaryAnalysisKeepsItsSmallerResourceBudget() = runBlocking {
        val engine = UnevenEngine()
        MoveAnalyzer(engine).analyze(emptyList(), "a2a3", deep = false)
        assertEquals(3, engine.calls.size)
        assertTrue(engine.calls.all { it.threads == 2 && it.hashMb == 128 && it.timeMs == 1500 })
        assertEquals(18, engine.calls.first().depth)
    }
}
