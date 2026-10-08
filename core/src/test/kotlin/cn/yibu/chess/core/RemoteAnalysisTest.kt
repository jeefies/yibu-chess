package cn.yibu.chess.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteAnalysisTest {
    private fun review(canCompare: Boolean = true, playedDepth: Int = 22, commonDepth: Int = 22): MoveReview = runBlocking {
        val service = object : StockfishService {
            override suspend fun evaluate(history: List<String>, profile: String, multiPv: Int): RemoteEvaluation = error("unused")
            override suspend fun analyzeMove(history: List<String>, playedMove: String, deep: Boolean) = RemoteMoveAnalysis(
                best = Evaluation(22, cp = 40, pv = listOf("e2e4")),
                played = Evaluation(playedDepth, cp = 30, pv = listOf(playedMove)),
                canCompare = canCompare, commonDepth = commonDepth)
            override fun stop() = Unit
        }
        MoveAnalyzer(service).analyze(emptyList(), "d2d4", true)
    }
    @Test fun serverRefusalCannotBeOverriddenByEqualDepths() {
        assertEquals(Grade.UNSTABLE, review(canCompare = false).grade)
        assertTrue(review(canCompare = false).provisional)
    }
    @Test fun serverApprovalCannotOverrideDifferentOrUnmatchedDepths() {
        assertEquals(Grade.UNSTABLE, review(playedDepth = 18).grade)
        assertEquals(Grade.UNSTABLE, review(commonDepth = 18).grade)
    }
    @Test fun completeComparisonUsesExistingRatingModelAndEngineVersion() {
        val review = review()
        assertEquals(Grade.EXCELLENT, review.grade)
        assertFalse(review.provisional)
        assertEquals("Stockfish 19", review.engineVersion)
    }
    @Test fun deepCacheRequiresMatchingEngineVersionAndElo() {
        val review = review()
        assertTrue(review.canReuseDeep(500, "Stockfish 19"))
        assertFalse(review.canReuseDeep(500, "Stockfish 17.1"))
        assertFalse(review.canReuseDeep(600, "Stockfish 19"))
    }
    @Test fun strongestNewGameUsesRemoteEngineAndNeverChangesElo() {
        val game = EloRules.newGame(PlayerProfile(500), Difficulty.STRONG, true)
        assertEquals("Stockfish 19", game.opponentEngine)
        assertFalse(game.rated)
        assertFalse(EloRules.eligible(game.copy(finished = true, result = "1-0")))
    }
}
