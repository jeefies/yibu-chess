package cn.yibu.chess.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GameHighlightsTest {
    private val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7", "d2d3", "b7b5")
    private fun review(ply: Int, loss: Double = 0.0): MoveReview {
        val ev = Evaluation(22, cp = 30, pv = moves.drop(ply - 1).take(4))
        return MoveReview(ply, moves[ply - 1], ChessRules.san(moves.take(ply - 1), moves[ply - 1]), ev, ev,
            grade = if (loss > 0) Grade.BLUNDER else Grade.GOOD, explanation = "", provisional = false,
            algorithmVersion = 3, deeplySearched = true, bestExpectedPoints = .8, playedExpectedPoints = .8 - loss)
    }

    @Test fun eitherColorShattersOnlyAtTheTransitionToCheckmateOrResignation() {
        val mates = listOf(listOf("f2f3", "e7e5", "g2g4", "d8h4"),
            listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"))
        mates.forEachIndexed { i, line ->
            val before = GameRecord(id = i.toLong(), moves = line.dropLast(1))
            val (result, ending) = ChessRules.outcome(line)!!
            val after = before.copy(moves = line, result = result, ending = ending, finished = true)
            val event = KingBreak.between(before, after)!!
            assertEquals(i == 0, event.white)
            assertEquals(if (event.white) 'K' else 'k', ChessRules.fenPieces(ChessRules.board(line).fen)[event.square])
            assertNull(KingBreak.between(after, after))
            assertNull(KingBreak.between(before.copy(id = 999), after))
        }
        listOf("1-0", "0-1").forEach { result ->
            val before = GameRecord()
            assertEquals(result == "0-1", KingBreak.between(before, before.copy(finished = true, result = result, ending = "认输"))!!.white)
            assertNull(KingBreak.between(before, before.copy(finished = true, result = "1/2-1/2", ending = "逼和")))
        }
    }

    @Test fun strongestMomentsAreSpacedChronologicalAndExplainLegalBranches() {
        val reviews = (1..12).map { review(it, when (it) { 2 -> .6; 3 -> .5; 6 -> .4; 10 -> .3; else -> 0.0 }) }
        val game = GameRecord(moves = moves, reviews = reviews, finished = true, result = "0-1", ending = "认输")
        val highlights = GameHighlights.build(game)
        assertTrue(highlights.size in 3..5)
        assertEquals(highlights.map { it.ply }.sorted(), highlights.map { it.ply })
        assertTrue(highlights.any { it.ply == 2 && it.title == "对手给出的机会" })
        assertFalse(highlights.any { it.ply == 3 })
        assertEquals("对局如何结束", highlights.last().title)
        assertTrue(highlights.last().reason.contains("不等于"))
        val analyzed = highlights.filter { it.lesson != null }
        analyzed.zipWithNext().forEach { (a, b) -> assertTrue(abs(a.ply - b.ply) >= 3) }
        analyzed.forEach { point ->
            val lesson = point.lesson!!
            assertEquals(lesson.variation, ChessRules.legalVariation(moves.take(point.ply - 1), lesson.variation))
            assertEquals(lesson.variation, lesson.steps.map { it.uci })
        }
        assertEquals(moves, game.moves)
        assertTrue(game.lessons.isEmpty())
    }

    @Test fun quietGamesHaveRecapsAndUnconfirmedOrStaleReviewsAreExcluded() {
        val reviews = (1..12).map { review(it) }
        val highlights = GameHighlights.build(GameRecord(moves = moves, reviews = reviews))
        assertEquals(3, highlights.size)
        assertTrue(highlights.all { it.title == "阶段回顾" })
        val unsafe = listOf(review(2, .8).copy(provisional = true), review(5, .8).copy(grade = Grade.UNSTABLE),
            review(8, .8).copy(uci = "a2a3"), review(11, .8).copy(algorithmVersion = 1))
        assertTrue(GameHighlights.build(GameRecord(moves = moves, reviews = unsafe)).isEmpty())
        assertTrue(GameHighlights.build(GameRecord()).isEmpty())
    }

    @Test fun evenAnUnanalyzedShortMateHasAnAccurateFinalPointWithoutAFakeRecommendation() {
        val line = listOf("f2f3", "e7e5", "g2g4", "d8h4")
        val point = GameHighlights.build(GameRecord(moves = line, finished = true, result = "0-1", ending = "将杀")).single()
        assertEquals(4, point.ply)
        assertTrue(point.reason.contains("黑方完成将杀"))
        assertNull(point.lesson)
    }
}
