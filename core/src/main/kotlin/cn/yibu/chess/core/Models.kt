package cn.yibu.chess.core

import kotlinx.serialization.Serializable

@Serializable
data class Evaluation(
    val depth: Int,
    val multiPv: Int = 1,
    val cp: Int? = null,
    val mate: Int? = null,
    val win: Int = 0,
    val draw: Int = 1000,
    val loss: Int = 0,
    val pv: List<String> = emptyList(),
) {
    val expected: Double get() = when {
        mate != null -> if (mate > 0) 1.0 else 0.0
        else -> (win + draw * 0.5) / (win + draw + loss).coerceAtLeast(1)
    }
    fun display(whitePerspective: Boolean = false, moverWhite: Boolean = true): String {
        val sign = if (whitePerspective && !moverWhite) -1 else 1
        return mate?.let { "${if (it * sign > 0) "+" else "−"}M${kotlin.math.abs(it)}" }
            ?: String.format(java.util.Locale.ROOT, "%+.2f", (cp ?: 0) * sign / 100.0)
    }
    fun whiteScore(moverWhite: Boolean): Double {
        val value = mate?.let { if (it > 0) 10.0 else -10.0 } ?: ((cp ?: 0) / 100.0)
        return if (moverWhite) value else -value
    }
}

@Serializable
enum class Grade(val symbol: String, val chinese: String) {
    BRILLIANT("!!", "精彩"), GREAT("!", "关键好棋"), BEST("✓", "最佳"), EXCELLENT("", "优秀"),
    GOOD("", "不错"), INACCURACY("?!", "不精确"), MISTAKE("?", "失误"), BLUNDER("??", "严重失误"),
    FORCED("", "被迫走法"), UNSTABLE("…", "待复评")
}

@Serializable
data class MoveReview(
    val ply: Int,
    val uci: String,
    val san: String,
    val best: Evaluation,
    val played: Evaluation,
    val second: Evaluation? = null,
    val grade: Grade,
    val explanation: String,
    val provisional: Boolean = true,
    val engineVersion: String = "Stockfish 17.1",
    val algorithmVersion: Int = 1,
) {
    val pointsLost: Double get() = (best.expected - played.expected).coerceAtLeast(0.0)
    val bestMove: String get() = best.pv.firstOrNull() ?: uci
    val moverWhite: Boolean get() = ply % 2 == 1
}

@Serializable
enum class Difficulty(val chinese: String, val description: String, val skill: Int) {
    RELAXED("轻松练习", "会出现可利用的失误，适合基础训练", 0),
    LIGHT("接近挑战", "减少失误，练习发现对手的威胁", 2),
    CHALLENGE("进阶挑战", "Stockfish 技能等级 5", 5),
    STRONG("最强", "全强度，限制每步思考时间", 20)
}

@Serializable
data class GameRecord(
    val id: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis(),
    val humanWhite: Boolean = true,
    val difficulty: Difficulty = Difficulty.RELAXED,
    val moves: List<String> = emptyList(),
    val reviews: List<MoveReview> = emptyList(),
    val result: String = "*",
    val ending: String = "",
    val finished: Boolean = false,
)

data class SearchRequest(
    val timeMs: Int = 500,
    val depth: Int = 0,
    val multiPv: Int = 3,
    val skill: Int = 20,
    val threads: Int = 1,
    val hashMb: Int = 64,
    val restricted: List<String> = emptyList(),
)

data class SearchResult(val bestMove: String, val snapshots: Map<Int, List<Evaluation>>) {
    val lines: List<Evaluation> get() = snapshots.maxByOrNull { it.key }?.value.orEmpty()
    val best: Evaluation get() = lines.firstOrNull() ?: error("引擎尚未返回完整评价，请重新分析")
}

interface ChessEngine {
    suspend fun search(history: List<String>, request: SearchRequest): SearchResult
    fun stop()
}
