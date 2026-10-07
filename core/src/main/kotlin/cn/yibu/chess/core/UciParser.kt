package cn.yibu.chess.core

object UciParser {
    fun parse(text: String, expectedLines: Int): SearchResult {
        val snapshots = sortedMapOf<Int, MutableMap<Int, Evaluation>>()
        var bestMove = ""
        text.lineSequence().forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.firstOrNull() == "bestmove") bestMove = fields.getOrNull(1).orEmpty()
            if (fields.firstOrNull() != "info" || "pv" !in fields || "lowerbound" in fields || "upperbound" in fields) return@forEach
            fun integer(key: String): Int? = fields.indexOf(key).takeIf { it >= 0 }?.let { fields.getOrNull(it + 1)?.toIntOrNull() }
            val depth = integer("depth") ?: return@forEach
            val scoreIndex = fields.indexOf("score")
            if (scoreIndex < 0) return@forEach
            val score = fields.getOrNull(scoreIndex + 2)?.toIntOrNull() ?: return@forEach
            val kind = fields.getOrNull(scoreIndex + 1)
            val wdl = fields.indexOf("wdl")
            if (wdl < 0 && kind != "mate") return@forEach
            val win = fields.getOrNull(wdl + 1)?.toIntOrNull() ?: 0
            val draw = fields.getOrNull(wdl + 2)?.toIntOrNull() ?: 0
            val loss = fields.getOrNull(wdl + 3)?.toIntOrNull() ?: 0
            if (kind != "mate" && win + draw + loss != 1000) return@forEach
            val evaluation = Evaluation(depth, integer("multipv") ?: 1,
                cp = if (kind == "cp") score else null, mate = if (kind == "mate") score else null,
                win = win, draw = draw, loss = loss,
                pv = fields.drop(fields.indexOf("pv") + 1).takeWhile { it.matches(Regex("[a-h][1-8][a-h][1-8][qrbn]?")) })
            if (evaluation.pv.isNotEmpty()) snapshots.getOrPut(depth) { sortedMapOf() }[evaluation.multiPv] = evaluation
        }
        val complete = snapshots.filterValues { rows -> (1..expectedLines).all { it in rows } }
            .mapValues { (_, rows) -> rows.values.sortedBy { it.multiPv } }
        if (complete.isEmpty()) error("引擎未返回完整候选，请重试")
        return SearchResult(bestMove, complete)
    }
}
