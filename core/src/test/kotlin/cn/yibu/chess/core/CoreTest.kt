package cn.yibu.chess.core

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        var nodes = 0L
        for (move in board.legalMoves()) { assertTrue(board.doMove(move, true)); nodes += perft(board, depth - 1); board.undoMove() }
        return nodes
    }
    @Test fun startingPositionHasCorrectMoveTree() {
        val board = Board()
        assertEquals(20L, perft(board, 1))
        assertEquals(400L, perft(board, 2))
        assertEquals(8902L, perft(board, 3))
    }
    @Test fun castlingAndEnPassantPreserveBoard() {
        val castle = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        assertTrue("e1g1" in ChessRules.legal(castle))
        val board = ChessRules.board(castle + "e1g1")
        assertEquals(Piece.WHITE_KING, board.getPiece(Square.G1))
        assertEquals(Piece.WHITE_ROOK, board.getPiece(Square.F1))
        val enPassant = listOf("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue("e5d6" in ChessRules.legal(enPassant))
        assertEquals(Piece.NONE, ChessRules.board(enPassant + "e5d6").getPiece(Square.D5))
    }
    @Test fun allFourPromotionsAreAvailable() {
        val board = Board().apply { loadFromFen("7k/P7/8/8/8/8/8/7K w - - 0 1") }
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), board.legalMoves().map { it.toString().lowercase() }.filter { it.startsWith("a7a8") }.toSet())
    }
    @Test fun matingAndDrawClaimsAreDistinct() {
        assertEquals("0-1", ChessRules.outcome(listOf("f2f3", "e7e5", "g2g4", "d8h4"))?.first)
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        assertNotNull(ChessRules.drawClaim(cycle + cycle))
        assertNull(ChessRules.outcome(cycle + cycle))
        assertEquals("五次重复局面", ChessRules.outcome(cycle + cycle + cycle + cycle)?.second)
    }
    @Test fun minorPiecesWithPossibleMatesAreNotDeadPositions() {
        assertFalse(ChessRules.deadMaterial(Board().apply { loadFromFen("7k/8/8/8/8/8/NN6/K7 w - - 0 1") }))
        assertFalse(ChessRules.deadMaterial(Board().apply { loadFromFen("7k/6n1/8/8/8/8/B7/K7 w - - 0 1") }))
        assertTrue(ChessRules.deadMaterial(Board().apply { loadFromFen("7k/8/8/8/8/8/B7/K7 w - - 0 1") }))
    }
    @Test fun pinnedEnPassantIsIllegal() {
        val board = Board().apply { loadFromFen("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1") }
        assertFalse(board.legalMoves().contains(Move("e5d6", board.sideToMove)))
    }
    @Test fun parserUsesDeepestCompleteMultipvAndRejectsBounds() {
        val output = """
            info depth 8 multipv 1 score cp 50 wdl 180 810 10 pv e2e4 e7e5
            info depth 8 multipv 2 score cp 40 wdl 140 850 10 pv d2d4 d7d5
            info depth 9 multipv 1 score cp 99 wdl 400 590 10 pv e2e4 e7e5
            info depth 9 multipv 2 score cp 80 lowerbound wdl 300 690 10 pv d2d4 d7d5
            bestmove e2e4
        """.trimIndent()
        val result = UciParser.parse(output, 2)
        assertEquals(8, result.best.depth)
        assertEquals(2, result.lines.size)
        assertEquals(0.585, result.best.expected, 0.0001)
    }
    @Test fun thresholdsUsePercentagePointsAndMateIsNotCentipawns() {
        assertEquals(Grade.GOOD, RatingRules.ordinary(0.02, false))
        assertEquals(Grade.INACCURACY, RatingRules.ordinary(0.05, false))
        assertEquals(Grade.MISTAKE, RatingRules.ordinary(0.10, false))
        assertEquals(Grade.BLUNDER, RatingRules.ordinary(0.20, false))
        assertEquals(1.0, Evaluation(14, mate = 3).expected, 0.0)
        assertEquals(0.0, Evaluation(14, mate = -3).expected, 0.0)
        assertEquals(-1.2, Evaluation(12, cp = 120).whiteScore(false), 0.0)
    }
    @Test fun analyzerComparesAtSameRootAndTurnsOffWeakPlay() = runBlocking {
        val history = listOf("e2e4")
        val calls = mutableListOf<Pair<List<String>, SearchRequest>>()
        val engine = object : ChessEngine {
            override fun stop() {}
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                calls += history to request
                val lines = if (request.restricted.isNotEmpty()) listOf(Evaluation(12, cp = -80, win = 100, draw = 650, loss = 250, pv = listOf("a7a6", "d2d4")))
                    else listOf(Evaluation(12, cp = 40, win = 250, draw = 650, loss = 100, pv = listOf("e7e5", "g1f3")),
                        Evaluation(12, 2, cp = 30, win = 200, draw = 700, loss = 100, pv = listOf("c7c5", "g1f3")),
                        Evaluation(12, 3, cp = 20, win = 170, draw = 730, loss = 100, pv = listOf("e7e6", "d2d4")))
                return SearchResult(lines.first().pv.first(), mapOf(12 to lines))
            }
        }
        val review = MoveAnalyzer(engine).analyze(history, "a7a6", false)
        assertEquals(Grade.MISTAKE, review.grade)
        assertEquals(0.15, review.pointsLost, 0.0001)
        assertTrue(calls.all { it.first == history && it.second.skill == 20 })
        assertEquals(listOf("a7a6"), calls.last().second.restricted)
        assertFalse(review.moverWhite)
    }
    @Test fun inconsistentSearchIsMarkedForReevaluation() = runBlocking {
        val engine = object : ChessEngine {
            override fun stop() {}
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                val pv = request.restricted.firstOrNull() ?: "e2e4"
                val expectedWin = if (request.restricted.isEmpty()) 100 else 500
                val lines = List(if (request.restricted.isEmpty()) 3 else 1) { i -> Evaluation(12, i + 1, cp = 20, win = expectedWin, draw = 1000 - expectedWin, pv = listOf(if (i == 0) pv else if (i == 1) "d2d4" else "g1f3")) }
                return SearchResult(pv, mapOf(12 to lines))
            }
        }
        assertEquals(Grade.UNSTABLE, MoveAnalyzer(engine).analyze(emptyList(), "a2a3", true).grade)
    }
    @Test fun pgnAndSanRoundTrip() {
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5")
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5"), ChessRules.sanMoves(moves))
        assertTrue(ChessRules.pgn(GameRecord(moves = moves)).contains("3. Bb5"))
    }
}
