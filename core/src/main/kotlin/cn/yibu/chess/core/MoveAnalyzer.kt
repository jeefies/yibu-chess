package cn.yibu.chess.core

import kotlin.math.abs

object RatingRules {
    fun ordinary(loss: Double, actualBest: Boolean): Grade = when {
        loss >= 0.20 - 1e-9 -> Grade.BLUNDER
        loss >= 0.10 - 1e-9 -> Grade.MISTAKE
        loss >= 0.05 - 1e-9 -> Grade.INACCURACY
        loss >= 0.02 - 1e-9 -> Grade.GOOD
        actualBest -> Grade.BEST
        else -> Grade.EXCELLENT
    }
}

class MoveAnalyzer(private val engine: ChessEngine) {
    suspend fun analyze(history: List<String>, uci: String, deep: Boolean): MoveReview {
        val legal = ChessRules.legal(history)
        require(uci in legal)
        val budget = if (deep) 1800 else 450
        val request = SearchRequest(timeMs = budget, multiPv = minOf(3, legal.size), threads = if (deep) 2 else 1, hashMb = if (deep) 128 else 64)
        var root = engine.search(history, request)
        var best = root.best
        var actual = root.lines.find { it.pv.firstOrNull() == uci }
        if (actual == null) {
            val forced = engine.search(history, request.copy(depth = best.depth, multiPv = 1, restricted = listOf(uci)))
            var commonDepth = root.snapshots.keys.intersect(forced.snapshots.keys).maxOrNull()
            if (commonDepth == null) {
                root = engine.search(history, request.copy(depth = minOf(best.depth, forced.best.depth)))
                commonDepth = root.snapshots.keys.intersect(forced.snapshots.keys).maxOrNull()
            }
            if (commonDepth != null) {
                best = root.snapshots.getValue(commonDepth).first()
                actual = forced.snapshots.getValue(commonDepth).first()
            } else actual = forced.best
        }
        var played = requireNotNull(actual)
        // Terminal game outcomes override statistical WDL, including mandatory draws.
        ChessRules.outcome(history + uci)?.let { (result, _) ->
            played = when (result) {
                "1/2-1/2" -> played.copy(mate = null, win = 0, draw = 1000, loss = 0)
                else -> played.copy(mate = 1, win = 1000, draw = 0, loss = 0)
            }
        }
        val sameDepth = best.depth == played.depth
        val negative = played.expected - best.expected > 0.025
        val loss = (best.expected - played.expected).coerceAtLeast(0.0)
        val second = root.snapshots[best.depth]?.getOrNull(1)
        val nearThreshold = listOf(0.02, 0.05, 0.10, 0.20).any { abs(loss - it) < 0.006 }
        val stable = sameDepth && !negative
        var grade = RatingRules.ordinary(loss, best.pv.firstOrNull() == uci)
        if (!stable) grade = Grade.UNSTABLE
        else if (legal.size == 1) grade = Grade.FORCED
        else if (loss < 0.02 && deep && best.depth >= 14) {
            val previous = root.snapshots.filterKeys { it < best.depth }.maxByOrNull { it.key }?.value?.firstOrNull()
            val stableBest = previous?.pv?.firstOrNull() == best.pv.firstOrNull() && previous != null && abs(previous.expected - best.expected) < 0.025
            if (stableBest && played.expected >= 0.50 && ChessRules.substantialSacrifice(history, played.pv)) grade = Grade.BRILLIANT
            else if (stableBest && second != null && best.expected - second.expected >= 0.10 && best.pv.firstOrNull() == uci && (best.mate == null || best.mate > 1)) grade = Grade.GREAT
        }
        val bestSan = ChessRules.san(history, best.pv.firstOrNull() ?: uci)
        val explanation = when (grade) {
            Grade.UNSTABLE -> "两次搜索尚未得到一致评价，建议深度复评后再判断。"
            Grade.FORCED -> "此处只有一着合法走法。"
            Grade.BRILLIANT -> "这次弃子在最佳应对下仍有充分补偿；可跟走推荐变化验证。"
            Grade.GREAT -> "这是关键好棋：其他候选会明显降低局面质量。"
            Grade.BEST -> "找到了引擎当前认为的最佳走法。"
            Grade.EXCELLENT, Grade.GOOD -> "这步保持了局面质量；推荐 $bestSan，可比较两条变化。"
            else -> ChessRules.replyExplanation(history, played) ?: "推荐 $bestSan，能更好地保持局面质量。点击两条变化比较。"
        } + if (best.mate != null && best.mate > 0 && played.mate == null) " 这步错过了引擎发现的强制将杀。" else ""
        return MoveReview(history.size + 1, uci, ChessRules.san(history, uci), best, played, second,
            grade, explanation, provisional = !deep || !stable || nearThreshold || best.depth < 12)
    }
}
