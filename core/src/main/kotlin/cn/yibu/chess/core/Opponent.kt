package cn.yibu.chess.core

/** Full-strength practice opponent; matched games use HumanOpponent. */
class Opponent(private val engine: ChessEngine) {
    suspend fun move(history: List<String>): String {
        val legal = ChessRules.legal(history)
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.single()
        val result = engine.search(history, SearchRequest(timeMs = 1500, multiPv = 1, skill = 20, threads = 2, hashMb = 128))
        return result.bestMove.also { require(it in legal) }
    }
}
