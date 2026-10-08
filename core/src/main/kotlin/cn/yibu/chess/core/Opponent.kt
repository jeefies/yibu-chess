package cn.yibu.chess.core

/** Full-strength practice opponent; matched games use HumanOpponent. */
class Opponent(private val service: StockfishService) {
    constructor(engine: ChessEngine) : this(EngineToServiceAdapter(engine))

    suspend fun move(history: List<String>): String {
        val legal = ChessRules.legal(history)
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.single()
        val result = service.evaluate(history, profile = "standard", multiPv = 1)
        return result.bestMove.also { require(it in legal) }
    }
}
