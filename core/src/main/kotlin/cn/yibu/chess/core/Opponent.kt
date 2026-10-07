package cn.yibu.chess.core

import kotlin.random.Random

class Opponent(private val engine: ChessEngine, private val random: Random = Random.Default) {
    suspend fun move(history: List<String>, difficulty: Difficulty): String {
        val legal = ChessRules.legal(history)
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        if (difficulty == Difficulty.STRONG || difficulty == Difficulty.CHALLENGE) {
            val result = engine.search(history, SearchRequest(timeMs = if (difficulty == Difficulty.STRONG) 1000 else 600,
                multiPv = 1, skill = difficulty.skill))
            require(result.bestMove in legal)
            return result.bestMove
        }
        val root = engine.search(history, SearchRequest(timeMs = 280, depth = 9, multiPv = minOf(6, legal.size)))
        val candidates = root.lines.toMutableList()
        // Include ordinary legal moves beyond Stockfish's strongest candidates.
        // These levels are practice settings, not calibrated Chess.com ratings.
        legal.filter { uci -> candidates.none { it.pv.firstOrNull() == uci } }.shuffled(random).take(3).forEach { uci ->
            val line = engine.search(history, SearchRequest(timeMs = 100, depth = minOf(7, root.best.depth), multiPv = 1, restricted = listOf(uci))).best
            candidates += line
        }
        val best = root.best
        fun cost(line: Evaluation): Double = when {
            line.mate != null && line.mate < 0 -> 2000.0
            best.mate != null && best.mate > 0 && line.mate == null -> 800.0
            else -> ((best.cp ?: 0) - (line.cp ?: 0)).toDouble().coerceAtLeast(0.0)
        }
        val roll = random.nextDouble()
        val relaxed = difficulty == Difficulty.RELAXED
        val range = when {
            roll < if (relaxed) 0.40 else 0.70 -> 0.0..60.0
            roll < if (relaxed) 0.82 else 0.94 -> 60.0..220.0
            else -> 220.0..550.0
        }
        val pool = candidates.filter { cost(it) in range }
        val chosen = if (pool.isNotEmpty()) pool.random(random)
            else candidates.minBy { kotlin.math.abs(cost(it) - (range.start + range.endInclusive) / 2) }
        return chosen.pv.first().also { require(it in legal) }
    }
}
