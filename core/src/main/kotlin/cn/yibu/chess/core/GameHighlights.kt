package cn.yibu.chess.core

import kotlin.math.abs

/** Transient end event. Loading a finished record never creates a celebration. */
data class KingBreak(val gameId: Long, val square: Int, val white: Boolean, val checkmate: Boolean) {
    companion object {
        fun between(before: GameRecord, after: GameRecord): KingBreak? {
            if (before.id != after.id || before.finished || !after.finished ||
                after.ending !in listOf("将杀", "认输") || after.result !in listOf("1-0", "0-1")) return null
            val white = after.result == "0-1"
            val square = ChessRules.fenPieces(ChessRules.board(after.moves).fen).indexOf(if (white) 'K' else 'k')
            return square.takeIf { it >= 0 }?.let { KingBreak(after.id, it, white, after.ending == "将杀") }
        }
    }
}

data class ReviewHighlight(val ply: Int, val title: String, val reason: String, val lesson: MoveLesson?)

/** Select spaced, chronological teaching moments, using confirmed reviews only. */
object GameHighlights {
    fun build(game: GameRecord): List<ReviewHighlight> {
        if (game.moves.isEmpty()) return emptyList()
        val reviews = game.reviews.filter { it.ply in 1..game.moves.size && it.uci == game.moves[it.ply - 1] &&
            !it.provisional && it.grade != Grade.UNSTABLE && it.algorithmVersion == 3 &&
            it.best.depth >= 12 && it.best.depth == it.played.depth }.distinctBy { it.ply }
        fun weight(review: MoveReview): Double = review.pointsLost * 100 + when {
            review.grade == Grade.BRILLIANT -> 100.0
            review.grade == Grade.GREAT -> 24.0
            review.best.mate != null && review.best.mate > 0 && (review.played.mate ?: 0) <= 0 -> 40.0
            else -> 0.0
        } + if (review.moverWhite == game.humanWhite) 2.0 else 0.0
        val selected = mutableListOf<MoveReview>()
        reviews.filter { it.pointsLost >= .04 || it.grade in listOf(Grade.BRILLIANT, Grade.GREAT) ||
            (it.best.mate != null && it.best.mate > 0 && (it.played.mate ?: 0) <= 0) }
            .sortedByDescending(::weight).forEach { review ->
                if (selected.size < 4 && selected.none { abs(it.ply - review.ply) < 3 }) selected += review
            }
        // Quiet games still get a short tour; these are labelled as recaps, not invented blunders.
        for (anchor in listOf(1, (game.moves.size + 1) / 2, game.moves.size)) {
            if (selected.size >= 3) break
            reviews.filter { candidate -> selected.none { abs(it.ply - candidate.ply) < 3 } }
                .minByOrNull { abs(it.ply - anchor) }?.let(selected::add)
        }
        val result = selected.map { review ->
            val actor = if (review.moverWhite == game.humanWhite) "你" else "对手"
            val missedMate = review.best.mate != null && review.best.mate > 0 && (review.played.mate ?: 0) <= 0
            val title = when {
                review.grade == Grade.BRILLIANT -> "精彩弃子 !!"
                missedMate -> "错过将杀机会"
                review.pointsLost >= .04 -> if (actor == "你") "值得改进的一步" else "对手给出的机会"
                review.grade == Grade.GREAT -> "关键好棋"
                else -> "阶段回顾"
            }
            val reason = buildString {
                append("${actor}走了 ${review.san}。")
                when {
                    review.grade == Grade.BRILLIANT -> append(review.brilliantReason ?: review.explanation)
                    missedMate -> append("当前搜索找到将杀路线，实战走法没能保留这条路线。")
                    review.pointsLost >= .04 -> append("这步让行棋方的预期得分下降约 ${kotlin.math.round(review.pointsLost * 100).toInt()} 个百分点。")
                    else -> append("看看这一步的布局和后续应对。")
                }
            }
            val lesson = runCatching { MoveCoach.explain(game.moves.take(review.ply - 1), review) }.getOrNull()
            ReviewHighlight(review.ply, title, reason, lesson)
        }.toMutableList()
        if (game.finished && result.none { it.ply == game.moves.size }) {
            result += ReviewHighlight(game.moves.size, "对局如何结束", when (game.ending) {
                "将杀" -> "${if (game.result == "1-0") "白方" else "黑方"}完成将杀，落败方已没有合法的解将方式。"
                "认输" -> "${if (game.result == "1-0") "黑方" else "白方"}认输，棋局在此结束；认输不等于当前局面已经将杀。"
                else -> "本局以${game.ending}结束。"
            }, null)
        }
        return result.sortedBy { it.ply }.take(5)
    }
}
