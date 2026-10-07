package cn.yibu.chess.core

import kotlin.random.Random

data class BotTuning(val bestChance: Double, val blunderChance: Double, val errorScale: Double)

object AdaptiveBot {
    fun tuning(elo: Int): BotTuning {
        val progress = (elo.coerceIn(100, 1800) - 100) / 1700.0
        return BotTuning(0.18 + 0.70 * progress, 0.36 * (1 - progress) * (1 - progress), 1.30 - 0.70 * progress)
    }
}

class Opponent(private val engine: ChessEngine, private val random: Random = Random.Default) {
    suspend fun move(history: List<String>, difficulty: Difficulty, opponentElo: Int = 500): String {
        val legal = ChessRules.legal(history)
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        if (difficulty == Difficulty.STRONG || opponentElo >= 1800) {
            val strong = difficulty == Difficulty.STRONG
            val result = engine.search(history, SearchRequest(timeMs = if (strong) 1500 else 700,
                multiPv = 1, skill = if (strong) 20 else ((opponentElo - 1400) / 70).coerceIn(0, 20),
                threads = if (strong) 2 else 1, hashMb = if (strong) 128 else 64))
            require(result.bestMove in legal)
            return result.bestMove
        }
        val root = engine.search(history, SearchRequest(timeMs = 350, depth = (6 + opponentElo / 250).coerceIn(6, 13), multiPv = minOf(6, legal.size)))
        val candidates = root.lines.toMutableList()
        // Include ordinary legal moves beyond Stockfish's strongest candidates.
        // This personal practice scale is not calibrated against online ratings.
        legal.filter { uci -> candidates.none { it.pv.firstOrNull() == uci } }.shuffled(random).take(5).forEach { uci ->
            val line = engine.search(history, SearchRequest(timeMs = 120, depth = minOf(8, root.best.depth), multiPv = 1, restricted = listOf(uci))).best
            candidates += line
        }
        val best = root.best
        fun cost(line: Evaluation): Double = when {
            line.mate != null && line.mate < 0 -> 2000.0
            best.mate != null && best.mate > 0 && line.mate == null -> 800.0
            else -> ((best.cp ?: 0) - (line.cp ?: 0)).toDouble().coerceAtLeast(0.0)
        }
        val roll = random.nextDouble()
        val tuning = AdaptiveBot.tuning(opponentElo)
        val range = when {
            roll < tuning.bestChance -> 0.0..45.0
            roll < 1.0 - tuning.blunderChance -> 45.0 * tuning.errorScale..220.0 * tuning.errorScale
            else -> 220.0 * tuning.errorScale..650.0 * tuning.errorScale
        }
        val pool = candidates.filter { cost(it) in range }
        val chosen = if (pool.isNotEmpty()) pool.random(random)
            else candidates.minBy { kotlin.math.abs(cost(it) - (range.start + range.endInclusive) / 2) }
        return chosen.pv.first().also { require(it in legal) }
    }
}
