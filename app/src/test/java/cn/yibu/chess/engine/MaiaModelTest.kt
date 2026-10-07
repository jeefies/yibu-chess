package cn.yibu.chess.engine

import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaiaModelTest {
    @Test fun actualBundledModelMatchesPythonAndProducesDiverseHumanReplies() = runBlocking {
        val model = MaiaModel(ApplicationProvider.getApplicationContext())
        model.initialize()
        val fixtures = JSONArray(javaClass.getResource("/maia/reference-fixtures.json")!!.readText())
        for (i in 0 until fixtures.length()) {
            val item = fixtures.getJSONObject(i)
            val moves = item.getJSONArray("history")
            val history = (0 until moves.length()).map(moves::getString)
            val logits = model.logits(history, 1000, 1000)
            assertEquals(4352, logits.size)
            val expected = item.getJSONObject("logits")
            for (key in expected.keys()) assertEquals("$history policy[$key]", expected.getDouble(key), logits[key.toInt()].toDouble(), 0.001)
            val candidates = HumanSampling.candidates(ChessRules.legal(history), logits, history.size % 2 == 0)
            assertEquals(1.0, candidates.sumOf { it.probability }, 1e-9)
        }
        val history = listOf("e2e4")
        val logits = model.logits(history, 1000, 1000)
        val cached = object : HumanPolicy {
            override suspend fun logits(history: List<String>, selfElo: Int, opponentElo: Int) = logits
        }
        val bot = HumanOpponent(cached)
        val game = EloRules.newGame(PlayerProfile(), humanWhite = true).copy(moves = history)
        val counts = (0L until 96L).map { bot.move(game.copy(policySeed = it)) }.groupingBy { it }.eachCount()
        println("Actual Maia-3 replies after e4 (96 independent games): $counts")
        assertTrue("The real model should offer several plausible replies: $counts", counts.size >= 3)
        assertTrue(counts.keys.all { it in ChessRules.legal(history) })
        assertTrue(counts.containsKey("e7e5"))
        assertEquals(bot.move(game), bot.move(game))
        val low = model.logits(history, 600, 600)
        val high = model.logits(history, 2600, 2600)
        assertTrue(low.indices.any { kotlin.math.abs(low[it] - high[it]) > 0.1 })
    }
}
