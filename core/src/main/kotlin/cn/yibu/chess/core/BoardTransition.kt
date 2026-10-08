package cn.yibu.chess.core

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move

data class PieceMotion(val from: Int, val to: Int, val piece: Char)
data class FadingPiece(val square: Int, val piece: Char, val appearing: Boolean)
data class BoardTransition(val motions: List<PieceMotion>, val fades: List<FadingPiece>) {
    companion object {
        /** Only adjacent legal positions animate. Arbitrary review jumps snap. */
        fun between(before: String, after: String): BoardTransition? {
            if (before == after) return null
            val forward = findMove(before, after)
            val backward = if (forward == null) findMove(after, before) else null
            val move = forward ?: backward ?: return null
            val reversed = forward == null
            val old = ChessRules.fenPieces(before)
            val next = ChessRules.fenPieces(after)
            val from = if (reversed) move.to.ordinal else move.from.ordinal
            val to = if (reversed) move.from.ordinal else move.to.ordinal
            val motions = mutableListOf(PieceMotion(from, to, old[from]))
            if (old[from].lowercaseChar() == 'k' && kotlin.math.abs(from - to) == 2) {
                val rank = from / 8 * 8
                val rookFrom = if (reversed) rank + if (from % 8 == 6) 5 else 3 else rank + if (to % 8 == 6) 7 else 0
                val rookTo = if (reversed) rank + if (from % 8 == 6) 7 else 0 else rank + if (to % 8 == 6) 5 else 3
                motions += PieceMotion(rookFrom, rookTo, old[rookFrom])
            }
            val movingSources = motions.map { it.from }.toSet()
            val movingTargets = motions.map { it.to }.toSet()
            val fades = (0..63).mapNotNull { square ->
                when {
                    !reversed && square !in movingSources && old[square] != ' ' && old[square] != next[square] ->
                        FadingPiece(square, old[square], false)
                    reversed && square !in movingTargets && next[square] != ' ' && old[square] != next[square] ->
                        FadingPiece(square, next[square], true)
                    else -> null
                }
            }
            return BoardTransition(motions, fades)
        }

        private fun findMove(fromFen: String, toFen: String): Move? {
            val board = Board().apply { loadFromFen(fromFen) }
            return board.legalMoves().firstOrNull { move ->
                board.doMove(move, true)
                val matches = board.fen == toFen
                board.undoMove()
                matches
            }
        }
    }
}
