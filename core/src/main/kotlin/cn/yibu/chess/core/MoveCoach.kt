package cn.yibu.chess.core

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/** Offline coaching: engine supplies the line; board facts supply the explanation. */
object MoveCoach {
    fun explain(history: List<String>, review: MoveReview): MoveLesson {
        require(review.ply == history.size + 1)
        val line = ChessRules.legalVariation(history, review.best.pv.take(10))
        require(line.isNotEmpty()) { "引擎没有返回合法推荐，请重试本步讲解" }
        val actor = if (history.size % 2 == 0) "白方" else "黑方"
        val san = ChessRules.san(history, line.first())
        val reasons = facts(history, line.first())
        val scoreNote = when {
            review.best.mate != null && review.best.mate > 0 -> "引擎在这条最佳防守变化中找到将杀路线，优先保持连续的威胁。"
            review.best.mate != null && review.best.mate < 0 -> "引擎仍判断本方会被将杀；这是当前防守候选，不能据此认为已经脱险。"
            (review.best.cp ?: 0) < -200 -> "本方当前仍处劣势；推荐走法是在劣势中寻找更好的抵抗，不代表已经扳回局面。"
            else -> "这是引擎当前搜索的最佳候选；目的要结合对手的防守和后续变化一起看。"
        }
        val why = buildString {
            append("建议${actor}走 $san。")
            append(reasons.joinToString("；").ifEmpty { "通过${action(history, line.first())}调整站位，衔接下方参考变化" })
            append("。\n").append(scoreNote)
            if (line.first() != review.uci) append("\n实战走的是 ${review.san}，可跟走下方变化，比较两种走法后的局面。")
        }
        val plan = buildString {
            val branch = line.take(6)
            branch.forEachIndexed { index, uci ->
                val root = history + line.take(index)
                val side = if (root.size % 2 == 0) "白方" else "黑方"
                val label = when (index) {
                    0 -> "先手计划"
                    1 -> "对手关键应对"
                    else -> if (index % 2 == 0) "继续思路" else "对手防守"
                }
                val note = facts(root, uci).take(2).joinToString("；").ifEmpty { "调整站位，衔接下一步变化" }
                append("${index + 1}. $label：$side ${ChessRules.san(root, uci)}（${action(root, uci)}）。$note。\n")
            }
            val ending = ChessRules.outcome(history + branch)
            if (ending != null) append("这条变化到此${ending.second}。\n")
            else if (line.size == 1) append("本次搜索没有给出更长的可靠变化，暂不推测对手的下一着。\n")
            append("这是引擎主变化的参考路线。对手若改走，先重新检查将军、吃子与直接威胁，不能机械照走。")
        }
        return MoveLesson(review.ply, line.first(), why, plan, line, review.best.depth)
    }

    private fun action(history: List<String>, uci: String): String {
        val board = ChessRules.board(history)
        val move = Move(uci, board.sideToMove)
        val piece = board.getPiece(move.from)
        if (piece.pieceType == PieceType.KING && kotlin.math.abs(move.to.ordinal - move.from.ordinal) == 2)
            return if (move.to.ordinal % 8 == 6) "王翼易位" else "后翼易位"
        return "${ChessRules.pieceChinese(piece)}从${move.from.toString().lowercase()}到${move.to.toString().lowercase()}"
    }

    private fun facts(history: List<String>, uci: String): List<String> {
        val before = ChessRules.board(history)
        val side = before.sideToMove
        val move = Move(uci, side)
        val piece = before.getPiece(move.from)
        val from = move.from.ordinal
        val to = move.to.ordinal
        val isPawn = piece.pieceType == PieceType.PAWN
        val enPassant = isPawn && from % 8 != to % 8 && before.getPiece(move.to) == Piece.NONE
        val captured = if (enPassant) before.getPiece(Square.squareAt(to + if (side == Side.WHITE) -8 else 8)) else before.getPiece(move.to)
        val after = ChessRules.board(history + uci)
        val moved = after.getPiece(move.to)
        val result = mutableListOf<String>()
        if (after.isMated) return listOf("这着直接将杀，对方已没有合法的解将方式")
        if (before.isKingAttacked) result += "先解除本方王受到的将军，避免忽略眼前最紧急的威胁"
        if (piece.pieceType == PieceType.KING && kotlin.math.abs(to - from) == 2)
            result += "通过易位把王移出中路，同时让车进入${if (to % 8 == 6) "f" else "d"}线"
        if (move.promotion != Piece.NONE) result += "兵到底线升变为${ChessRules.pieceChinese(move.promotion)}，增加可用子力"
        if (captured != Piece.NONE) result += "${if (enPassant) "吃掉相邻线的兵（吃过路兵）" else "直接吃掉${move.to.toString().lowercase()}的${ChessRules.pieceChinese(captured)}"}；是否能保持子力收益还要看对方回吃"
        if (after.isKingAttacked) result += "将军迫使对手先处理王的威胁，为后续变化争取节奏"
        val enemies = Square.entries.filter { it != Square.NONE && after.getPiece(it) != Piece.NONE && after.getPiece(it).pieceSide != side }
        val targets = enemies.filter { after.squareAttackedBy(it, side) and move.to.bitboard != 0L }
        val valuable = targets.filter { after.getPiece(it).pieceType in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.KING) }
        if (valuable.size >= 2) result += "${ChessRules.pieceChinese(moved)}同时瞄准${valuable.joinToString("和") { "${it.toString().lowercase()}的${ChessRules.pieceChinese(after.getPiece(it))}" }}；对方仍可能解围，需核对后续应对"
        else targets.firstOrNull { after.getPiece(it).pieceType != PieceType.PAWN && after.getPiece(it).pieceType != PieceType.KING }?.let {
            result += "新站位瞄准${it.toString().lowercase()}的${ChessRules.pieceChinese(after.getPiece(it))}，给对方增加需要处理的威胁"
        }
        val homeRank = if (side == Side.WHITE) 0 else 7
        if (piece.pieceType in listOf(PieceType.KNIGHT, PieceType.BISHOP) && from / 8 == homeRank && to / 8 != homeRank && history.size < 24)
            result += "把${ChessRules.pieceChinese(piece)}从底线发展出来，让它参与争夺局面"
        val centers = listOf(Square.D4, Square.E4, Square.D5, Square.E5)
        val controlled = centers.filter { after.squareAttackedBy(it, side) and move.to.bitboard != 0L }
        if (isPawn && move.to in centers) result += "兵占据${move.to.toString().lowercase()}中心格，为子力活动建立据点"
        if (controlled.isNotEmpty()) result += "${ChessRules.pieceChinese(moved)}控制${controlled.joinToString("、") { it.toString().lowercase() }}，增加对中心的影响"
        val defended = Square.entries.firstOrNull { sq ->
            sq != Square.NONE && sq != move.to && after.getPiece(sq) != Piece.NONE && after.getPiece(sq).pieceSide == side &&
                after.getPiece(sq).pieceType != PieceType.KING && before.squareAttackedBy(sq, side.flip()) != 0L &&
                after.squareAttackedBy(sq, side) and move.to.bitboard != 0L && before.squareAttackedBy(sq, side) and move.from.bitboard == 0L
        }
        if (defended != null) result += "新增对${defended.toString().lowercase()}${ChessRules.pieceChinese(after.getPiece(defended))}的保护，应对它原先受到的攻击"
        if (moved.pieceType == PieceType.ROOK) {
            val pawns = Square.entries.filter { it != Square.NONE && it.ordinal % 8 == to % 8 && after.getPiece(it).pieceType == PieceType.PAWN }
            if (pawns.none { after.getPiece(it).pieceSide == side }) result += if (pawns.isEmpty()) "车来到没有兵遮挡的开放线，便于沿线活动" else "车来到没有己方兵遮挡的半开放线，可沿线关注对方兵的弱点"
        }
        if (isPawn && move.promotion == Piece.NONE) {
            val blockers = enemies.any { sq -> after.getPiece(sq).pieceType == PieceType.PAWN && kotlin.math.abs(sq.ordinal % 8 - to % 8) <= 1 &&
                if (side == Side.WHITE) sq.ordinal / 8 > to / 8 else sq.ordinal / 8 < to / 8 }
            if (!blockers && (to / 8 in 3..6 && side == Side.WHITE || to / 8 in 1..4 && side == Side.BLACK))
                result += "向前推进通路兵；同线和相邻线前方没有敌兵，后续仍需保护它免被其他棋子吃掉"
        }
        if (after.isStaleMate) result += "这着让对方无合法走法但王未被将军，形成逼和"
        if (ChessRules.deadMaterial(after)) result += "变化后子力已不足以将杀，形成和棋"
        return result.distinct().take(4)
    }
}
