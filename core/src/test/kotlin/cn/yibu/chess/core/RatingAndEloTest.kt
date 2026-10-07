package cn.yibu.chess.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RatingAndEloTest {
    @Test fun equalEngineDrawProbabilitiesDoNotHideAMaterialBlunder() = runBlocking {
        val engine = object : ChessEngine {
            override fun stop() {}
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                val forced = request.restricted.isNotEmpty()
                val lines = if (forced) listOf(Evaluation(14, cp = -150, pv = listOf("a2a3")))
                    else listOf(Evaluation(14, cp = 150, pv = listOf("e2e4")),
                        Evaluation(14, 2, cp = 140, pv = listOf("d2d4")),
                        Evaluation(14, 3, cp = 130, pv = listOf("g1f3")))
                return SearchResult(lines.first().pv.first(), mapOf(14 to lines))
            }
        }
        val review = MoveAnalyzer(engine).analyze(emptyList(), "a2a3", false, 500)
        assertEquals(0.5, review.best.expected, 0.0)
        assertEquals(0.5, review.played.expected, 0.0)
        assertEquals(Grade.BLUNDER, review.grade)
        assertTrue(review.pointsLost > 0.20)
        assertEquals(3, review.algorithmVersion)
        assertEquals(500, review.scoringElo)
    }
    @Test fun equalCentipawnCandidatesAreBestAndSmallLossIsExcellent() = runBlocking {
        val engine = object : ChessEngine {
            override fun stop() {}
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                val lines = listOf(Evaluation(14, cp = 40, pv = listOf("e2e4")),
                    Evaluation(14, 2, cp = 40, pv = listOf("d2d4")),
                    Evaluation(14, 3, cp = 30, pv = listOf("g1f3")))
                return SearchResult("e2e4", mapOf(14 to lines))
            }
        }
        val analyzer = MoveAnalyzer(engine)
        assertEquals(Grade.BEST, analyzer.analyze(emptyList(), "d2d4", false).grade)
        val excellent = analyzer.analyze(emptyList(), "g1f3", false)
        assertEquals(Grade.EXCELLENT, excellent.grade)
        assertTrue(excellent.pointsLost > 0 && excellent.pointsLost < 0.02)
    }
    @Test fun bestMoveRemainsBestAtPublicBoundaryAndMateHasExactPoints() {
        assertEquals(Grade.BEST, RatingRules.ordinary(0.02, true))
        assertEquals(Grade.EXCELLENT, RatingRules.ordinary(0.019, false))
        assertEquals(1.0, RatingRules.expectedPoints(Evaluation(14, mate = 6), 500), 0.0)
        assertEquals(0.0, RatingRules.expectedPoints(Evaluation(14, mate = -6), 500), 0.0)
        assertEquals(0.5, RatingRules.expectedPoints(Evaluation(14, cp = 0), 500), 0.0)
        assertTrue(RatingRules.expectedPoints(Evaluation(14, cp = 100), 2400) > RatingRules.expectedPoints(Evaluation(14, cp = 100), 500))
    }
    @Test fun automaticDrawOverridesAnOptimisticSearchEvenForBestMove() = runBlocking {
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        val history = (cycle + cycle + cycle + cycle).dropLast(1)
        val engine = object : ChessEngine {
            override fun stop() {}
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                val lines = listOf(Evaluation(14, cp = 500, pv = listOf("f6g8")),
                    Evaluation(14, 2, cp = 400, pv = listOf("b8c6")),
                    Evaluation(14, 3, cp = 300, pv = listOf("e7e5")))
                return SearchResult("f6g8", mapOf(14 to lines))
            }
        }
        val review = MoveAnalyzer(engine).analyze(history, "f6g8", false)
        assertEquals(Grade.BEST, review.grade)
        assertEquals(0.5, requireNotNull(review.playedExpectedPoints), 0.0)
        assertEquals(0.0, review.pointsLost, 0.0)
    }
    @Test fun scoresFollowHumanColorAndBothModesSnapshotPersonalRating() {
        val profile = PlayerProfile(632, 4)
        val white = EloRules.newGame(profile, Difficulty.MATCHED, true)
        val black = EloRules.newGame(profile, Difficulty.MATCHED, false)
        assertEquals(632, white.opponentElo)
        assertEquals(632, black.playerEloAtStart)
        assertEquals(1.0, requireNotNull(EloRules.score(white.copy(result = "1-0"))), 0.0)
        assertEquals(0.0, requireNotNull(EloRules.score(black.copy(result = "1-0"))), 0.0)
        assertEquals(1.0, requireNotNull(EloRules.score(black.copy(result = "0-1"))), 0.0)
        assertEquals(0.5, requireNotNull(EloRules.score(white.copy(result = "1/2-1/2"))), 0.0)
        assertFalse(EloRules.eligible(white))
        assertTrue(EloRules.eligible(white.copy(finished = true, result = "1-0")))
        val strong = EloRules.newGame(profile, Difficulty.STRONG, true)
        assertFalse(strong.rated)
        assertNull(strong.opponentElo)
        assertFalse(EloRules.eligible(strong.copy(finished = true, result = "1-0", rated = true, opponentElo = 2800)))
        assertEquals(listOf(Difficulty.MATCHED, Difficulty.STRONG), Difficulty.choices)
    }
    @Test fun eloAdjustsFasterAtFirstAndHasBounds() {
        assertEquals(532, EloRules.calculate(PlayerProfile(), 500, 1.0).after)
        assertEquals(468, EloRules.calculate(PlayerProfile(), 500, 0.0).after)
        assertEquals(500, EloRules.calculate(PlayerProfile(), 500, 0.5).after)
        assertEquals(516, EloRules.calculate(PlayerProfile(500, 10), 500, 1.0).after)
        assertEquals(512, EloRules.calculate(PlayerProfile(500, 30), 500, 1.0).after)
        assertEquals(100, EloRules.calculate(PlayerProfile(100), 100, 0.0).after)
        assertEquals(2800, EloRules.calculate(PlayerProfile(2800), 2800, 1.0).after)
        assertTrue(EloRules.calculate(PlayerProfile(), 900, 1.0).delta > 32)
    }
    @Test fun olderSavedGamesKeepLegacyReviewsAndAreNotRatedRetroactively() {
        val old = Json.decodeFromString<GameRecord>("""{"id":123,"difficulty":"RELAXED","result":"1-0","finished":true} """)
        assertFalse(old.rated)
        assertEquals(Difficulty.MATCHED, old.mode)
        assertFalse(EloRules.eligible(old))
        val first = GameRecord()
        val second = GameRecord()
        assertNotEquals(first.id, second.id)
    }
    @Test fun humanModelRatingMappingIsMonotoneAndBounded() {
        assertEquals(600, HumanSkill.modelElo(100))
        assertEquals(1000, HumanSkill.modelElo(500))
        assertTrue(HumanSkill.modelElo(800) < HumanSkill.modelElo(1500))
        assertEquals(2600, HumanSkill.modelElo(2800))
    }
}
