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
        assertTrue(engine.calls.all { it.threads == 8 && it.hashMb == 512 && it.timeMs == 6000 && it.reuseSearch })
        assertEquals(2, engine.calls.first().multiPv)
        assertEquals(22, engine.calls.first().depth)
        assertEquals(listOf("a2a3"), engine.calls[1].restricted)
        assertEquals(16, engine.calls.last().depth)
        assertEquals(review.best.depth, review.played.depth)
    }

    @Test fun ordinaryAnalysisKeepsItsSmallerResourceBudget() = runBlocking {
        val engine = UnevenEngine()
        MoveAnalyzer(engine).analyze(emptyList(), "a2a3", deep = false)
        assertEquals(3, engine.calls.size)
        assertTrue(engine.calls.all { it.threads == 2 && it.hashMb == 128 && it.timeMs == 1500 && !it.reuseSearch })
        assertEquals(3, engine.calls.first().multiPv)
        assertEquals(18, engine.calls.first().depth)
    }

    @Test fun stableDeepResultsCanBeReusedEvenNearAClassificationBoundary() {
        val evaluation = Evaluation(18, cp = 30, pv = listOf("e2e4"))
        val review = MoveReview(1, "e2e4", "e4", evaluation, evaluation, grade = Grade.GOOD,
            explanation = "", provisional = true, algorithmVersion = 3, scoringElo = 500, deeplySearched = true)
        assertTrue(review.canReuseDeep(500))
        assertFalse(review.canReuseDeep(600))
        assertFalse(review.copy(deeplySearched = false).canReuseDeep(500))
        assertFalse(review.copy(grade = Grade.UNSTABLE).canReuseDeep(500))
        assertFalse(review.copy(played = evaluation.copy(depth = 17)).canReuseDeep(500))
        assertFalse(review.copy(algorithmVersion = 2).canReuseDeep(500))
        assertTrue(review.copy(deeplySearched = false, provisional = false).canReuseDeep(500))
    }
}
