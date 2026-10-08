package cn.yibu.chess.core

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CoachingAndMotionTest {
    private fun review(history: List<String>, pv: List<String>, mate: Int? = null): MoveReview {
        val eval = Evaluation(22, cp = 30, mate = mate, pv = pv)
        val move = ChessRules.legal(history).first()
        return MoveReview(history.size + 1, move, ChessRules.san(history, move), eval, eval,
            grade = Grade.GOOD, explanation = "", provisional = false)
    }
    @Test fun openingLessonExplainsCenterAndOnlyUsesLegalEngineReplies() {
        val pv = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6")
        val lesson = MoveCoach.explain(emptyList(), review(emptyList(), pv))
        assertTrue(lesson.why.contains("兵占据e4中心格"))
        assertTrue(lesson.plan.contains("对手关键应对：黑方 e5"))
        assertTrue(lesson.plan.contains("继续思路：白方 Nf3"))
        assertTrue(lesson.plan.contains("把马从底线发展出来"))
        assertEquals(pv, lesson.variation)
        assertEquals(1, lesson.ply)
    }
    @Test fun invalidEngineSuffixIsTruncatedRatherThanExplained() {
        val pv = listOf("e2e4", "e7e4", "g1f3")
        val lesson = MoveCoach.explain(emptyList(), review(emptyList(), pv))
        assertEquals(listOf("e2e4"), lesson.variation)
        assertFalse(lesson.plan.contains("Nf3"))
        assertTrue(lesson.plan.contains("没有给出更长"))
    }
    @Test fun captureAndEnPassantAreDescribedWithoutClaimingGuaranteedMaterial() {
        val history = listOf("e2e4", "a7a6", "e4e5", "d7d5")
        val lesson = MoveCoach.explain(history, review(history, listOf("e5d6", "c7d6")))
        assertTrue(lesson.why.contains("吃过路兵"))
        assertTrue(lesson.why.contains("对方回吃"))
        assertTrue(lesson.plan.contains("对手关键应对：黑方 cxd6"))
    }
    @Test fun castleLessonExplainsBothKingAndRook() {
        val history = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        val lesson = MoveCoach.explain(history, review(history, listOf("e1g1", "f8c5")))
        assertTrue(lesson.why.contains("王移出中路"))
        assertTrue(lesson.why.contains("车进入f线"))
        assertTrue(lesson.plan.contains("O-O"))
    }
    @Test fun actualMateEndsThePlanWithoutInventedFollowUp() {
        val history = listOf("f2f3", "e7e5", "g2g4")
        val lesson = MoveCoach.explain(history, review(history, listOf("d8h4", "e2e3"), 1))
        assertTrue(lesson.why.contains("建议黑方走 Qh4#"))
        assertTrue(lesson.why.contains("直接将杀"))
        assertTrue(lesson.plan.contains("到此将杀"))
        assertFalse(lesson.plan.contains("白方 e3"))
        assertEquals(4, lesson.ply)
    }
    @Test fun oldRecordsDecodeAndLessonsRoundTripWithoutChangingMoves() {
        val old = Json.decodeFromString<GameRecord>("""{"id":8,"moves":["e2e4"]}""")
        assertTrue(old.lessons.isEmpty())
        val lesson = MoveCoach.explain(emptyList(), review(emptyList(), listOf("e2e4", "e7e5")))
        val updated = old.copy(lessons = listOf(lesson))
        assertEquals(updated, Json.decodeFromString<GameRecord>(Json.encodeToString(GameRecord.serializer(), updated)))
    }
    @Test fun pacingCountsSearchTimeAndNeverAddsDelayToASlowSearch() {
        val random = Random(42)
        repeat(100) { assertTrue(OpponentPacing.targetMs(random) in 2_000..4_000) }
        assertEquals(2_500L, OpponentPacing.remainingMs(3_000, 500))
        assertEquals(0L, OpponentPacing.remainingMs(3_000, 6_000))
    }
    private fun transition(history: List<String>, move: String, reverse: Boolean = false): BoardTransition {
        val before = ChessRules.board(history).fen
        val after = ChessRules.board(history + move).fen
        return requireNotNull(if (reverse) BoardTransition.between(after, before) else BoardTransition.between(before, after))
    }
    @Test fun forwardAndBackwardCastlingMoveBothPieces() {
        val history = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        assertEquals(listOf(PieceMotion(4, 6, 'K'), PieceMotion(7, 5, 'R')), transition(history, "e1g1").motions)
        assertEquals(listOf(PieceMotion(6, 4, 'K'), PieceMotion(5, 7, 'R')), transition(history, "e1g1", true).motions)
    }
    @Test fun enPassantFadesTheVictimOnItsActualSquareAndRestoresItOnRewind() {
        val history = listOf("e2e4", "a7a6", "e4e5", "d7d5")
        assertEquals(listOf(FadingPiece(35, 'p', false)), transition(history, "e5d6").fades)
        assertEquals(listOf(FadingPiece(35, 'p', true)), transition(history, "e5d6", true).fades)
        assertEquals(listOf(PieceMotion(43, 36, 'P')), transition(history, "e5d6", true).motions)
    }
    @Test fun promotionsTravelAsPawnAndReverseAsPromotedPiece() {
        val before = "7k/P7/8/8/8/8/8/7K w - - 0 1"
        for (promotion in listOf("q", "r", "b", "n")) {
            val board = Board().apply { loadFromFen(before); doMove(Move("a7a8$promotion", sideToMove), true) }
            assertEquals(listOf(PieceMotion(48, 56, 'P')), BoardTransition.between(before, board.fen)?.motions)
            assertEquals(listOf(PieceMotion(56, 48, promotion.uppercase().first())), BoardTransition.between(board.fen, before)?.motions)
        }
    }
    @Test fun ordinaryCapturesFadeAndUnrelatedPositionsSnap() {
        val history = listOf("e2e4", "d7d5")
        assertEquals(listOf(FadingPiece(35, 'p', false)), transition(history, "e4d5").fades)
        assertNull(BoardTransition.between(ChessRules.START_FEN, ChessRules.board(history).fen))
        assertNull(BoardTransition.between(ChessRules.START_FEN, ChessRules.START_FEN))
    }
}
