package cn.yibu.chess.core

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.abs

enum class SoundCue { MOVE, CAPTURE, CASTLE, PROMOTE, CHECK, CHECKMATE, RESIGN, SHATTER, WIN, LOSE, DRAW,
    START, SELECT, ILLEGAL, NAVIGATE, CONFIRM, DELETE, ERROR, BRILLIANT }
data class SoundBeat(val cue: SoundCue, val delayMs: Long = 0)

/** Actual gameplay and review use distinct routes: a saved result never celebrates again. */
object SoundEvents {
    fun move(history: List<String>, uci: String): List<SoundBeat> {
        val before = ChessRules.board(history)
        val move = Move(uci, before.sideToMove)
        require(move in before.legalMoves())
        val piece = before.getPiece(move.from)
        val capture = before.getPiece(move.to) != Piece.NONE ||
            (piece.pieceType == PieceType.PAWN && move.from.ordinal % 8 != move.to.ordinal % 8)
        val cue = when {
            move.promotion != Piece.NONE -> SoundCue.PROMOTE
            piece.pieceType == PieceType.KING && abs(move.to.ordinal - move.from.ordinal) == 2 -> SoundCue.CASTLE
            capture -> SoundCue.CAPTURE
            else -> SoundCue.MOVE
        }
        check(before.doMove(move, true))
        val after = before
        return buildList {
            add(SoundBeat(if (capture && cue == SoundCue.PROMOTE) SoundCue.CAPTURE else cue))
            if (capture && cue == SoundCue.PROMOTE) add(SoundBeat(SoundCue.PROMOTE, 90))
            if (after.isMated) add(SoundBeat(SoundCue.CHECKMATE, 140))
            else if (after.isKingAttacked) add(SoundBeat(SoundCue.CHECK, 140))
        }
    }

    fun transition(before: GameRecord, after: GameRecord): List<SoundBeat> {
        if (before.id != after.id || before.finished) return emptyList()
        val appended = after.moves.size == before.moves.size + 1 && after.moves.dropLast(1) == before.moves
        val beats = if (appended) move(before.moves, after.moves.last()).toMutableList() else mutableListOf()
        if (after.finished) {
            val end = when {
                after.ending == "认输" -> SoundCue.RESIGN
                after.result == "1/2-1/2" -> SoundCue.DRAW
                after.ending == "将杀" -> SoundCue.CHECKMATE
                else -> null
            }
            if (end != null && beats.none { it.cue == end }) beats += SoundBeat(end, if (appended) 140 else 0)
            if (after.result in listOf("1-0", "0-1")) {
                val won = (after.result == "1-0") == after.humanWhite
                beats += SoundBeat(if (won) SoundCue.WIN else SoundCue.LOSE, if (after.ending == "将杀") 1100 else 850)
            }
        }
        return beats
    }

    fun preview(before: List<String>, after: List<String>): List<SoundBeat> = when {
        before == after -> emptyList()
        after.size == before.size + 1 && after.dropLast(1) == before -> move(before, after.last())
        else -> listOf(SoundBeat(SoundCue.NAVIGATE))
    }
}
