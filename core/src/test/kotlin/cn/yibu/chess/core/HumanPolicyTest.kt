package cn.yibu.chess.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln
import kotlin.random.Random

class HumanPolicyTest {
    @Test fun encodingMatchesUpstreamPythonForBothColorsAndSpecialMoves() {
        val fixtures = Json.parseToJsonElement(javaClass.getResource("/maia/reference-fixtures.json")!!.readText()).jsonArray
        assertEquals(9, fixtures.size)
        for (entry in fixtures) {
            val item = entry.jsonObject
            val history = item.getValue("history").jsonArray.map { it.jsonPrimitive.content }
            val encoded = MaiaEncoding.tokens(history)
            assertEquals(6144, encoded.size)
            assertTrue(encoded.all { it == 0f || it == 1f })
            assertEquals(history.toString(), item.getValue("tokenOnes").jsonArray.map { it.jsonPrimitive.int },
                encoded.indices.filter { encoded[it] == 1f })
            assertEquals(item.getValue("legalIndices").jsonArray.map { it.jsonPrimitive.int }.sorted(),
                ChessRules.legal(history).map { MaiaEncoding.index(it, history.size % 2 == 0) }.sorted())
        }
    }
    @Test fun randomColorsVaryAndExplicitColorsAreRespected() {
        val games = (0..63).map { EloRules.newGame(PlayerProfile(), random = Random(it)) }
        assertEquals(setOf(true, false), games.map { it.humanWhite }.toSet())
        assertEquals(64, games.map { it.policySeed }.toSet().size)
        assertTrue(games.all { it.rated && it.opponentEngine == "Maia-3 5M" && it.modelElo == 1000 })
        for (seed in 0..15) {
            assertTrue(EloRules.newGame(PlayerProfile(), humanWhite = true, random = Random(seed)).humanWhite)
            assertFalse(EloRules.newGame(PlayerProfile(), humanWhite = false, random = Random(seed)).humanWhite)
        }
        assertEquals(ColorPreference.RANDOM, PlaySettings().color)
    }
    @Test fun samplingStaysInPlausibleLegalPoolAndPenalizesRecentRepeats() {
        val legal = listOf("e2e4", "d2d4", "g1f3", "a2a3")
        val logits = FloatArray(4352) { Float.NaN }
        listOf(.6, .35, .049, .001).forEachIndexed { i, p -> logits[MaiaEncoding.index(legal[i], true)] = ln(p).toFloat() }
        val normal = HumanSampling.candidates(legal, logits, true)
        val adjusted = HumanSampling.candidates(legal, logits, true, repeats = mapOf("e2e4" to 12))
        assertEquals(legal.take(3), normal.map { it.move })
        assertEquals(normal.map { it.move }, adjusted.map { it.move })
        assertTrue(adjusted.first().probability < normal.first().probability / 2)
        assertEquals(1.0, adjusted.sumOf { it.probability }, 1e-9)
        assertTrue((0..99).map { HumanSampling.choose(normal, Random(it)) }.all { it in legal.take(3) })
    }
    @Test fun invalidModelOutputFailsInsteadOfPlayingArbitraryChess() {
        try {
            HumanSampling.candidates(ChessRules.legal(emptyList()), FloatArray(4352) { Float.NaN }, true)
            fail("Invalid probabilities must not silently turn into random legal moves")
        } catch (_: IllegalStateException) { }
    }
    @Test fun resumingOlderGamesPreservesColorHistoryAndRatingEligibility() {
        val old = GameRecord(humanWhite = false, difficulty = Difficulty.RELAXED, moves = listOf("e2e4"))
        val resumed = HumanOpponent.prepare(old)
        assertEquals("Maia-3 5M", resumed.opponentEngine)
        assertEquals(1000, resumed.modelElo)
        assertEquals(old.moves, resumed.moves)
        assertEquals(old.humanWhite, resumed.humanWhite)
        assertFalse(resumed.rated)
        assertEquals(resumed.policySeed, HumanOpponent.prepare(resumed).policySeed)
        assertEquals(old.copy(finished = true), HumanOpponent.prepare(old.copy(finished = true)))
        val zeroSeed = resumed.copy(policySeed = 0)
        assertEquals(0L, HumanOpponent.prepare(zeroSeed).policySeed)
        val strong = EloRules.newGame(PlayerProfile(), Difficulty.STRONG, true)
        assertEquals(strong, HumanOpponent.prepare(strong))
    }
    @Test fun humanOpponentUsesRatingAndFreshSeedsWithRestartStability() = runBlocking {
        var ratings: Pair<Int, Int>? = null
        val policy = object : HumanPolicy {
            override suspend fun logits(history: List<String>, selfElo: Int, opponentElo: Int): FloatArray {
                ratings = selfElo to opponentElo
                return FloatArray(4352)
            }
        }
        val bot = HumanOpponent(policy)
        val game = EloRules.newGame(PlayerProfile(700), humanWhite = true, random = Random(1)).copy(moves = listOf("e2e4"))
        val first = bot.move(game)
        assertEquals(first, bot.move(game))
        assertEquals(1200 to 1200, ratings)
        val replies = (0..63).map { bot.move(game.copy(policySeed = it.toLong())) }.toSet()
        assertTrue(replies.size >= 5)
        assertTrue(replies.all { it in ChessRules.legal(game.moves) })
        val humanMoves = (0..11).map { GameRecord(humanWhite = false, moves = listOf("e2e4", "e7e5")) }
        for (seed in 0..63) {
            val sample = game.copy(policySeed = seed.toLong())
            assertEquals("User moves must not count as repeated AI replies", bot.move(sample), bot.move(sample, humanMoves))
        }
    }
    @Test fun brilliantRequiresStableSacrificeAndExplainsActualContinuation() = runBlocking {
        val history = "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7 g5e7 d8e7 g1f3 e8g8 f1d3 c7c5".split(" ")
        val pv = "d3h7 g8h7 f3g5 h7g8 d1h5 f8e8 h5h7 g8f8".split(" ")
        assertEquals(pv, ChessRules.legalVariation(history, pv))
        assertTrue(ChessRules.substantialSacrifice(history, pv))
        val engine = object : ChessEngine {
            override fun stop() { }
            override suspend fun search(history: List<String>, request: SearchRequest): SearchResult {
                val snapshots = listOf(13, 14).associateWith { depth -> listOf(
                    Evaluation(depth, cp = 200, pv = pv),
                    Evaluation(depth, 2, cp = 50, pv = listOf("d4c5")),
                    Evaluation(depth, 3, cp = 0, pv = listOf("e1g1"))) }
                return SearchResult(pv.first(), snapshots)
            }
        }
        val analyzer = MoveAnalyzer(engine)
        assertNotEquals(Grade.BRILLIANT, analyzer.analyze(history, pv.first(), false).grade)
        val verified = analyzer.analyze(history, pv.first(), true)
        assertEquals(Grade.BRILLIANT, verified.grade)
        assertFalse(verified.provisional)
        assertTrue(verified.brilliantReason!!.contains("弃象"))
        assertTrue(verified.brilliantPlan!!.contains("Kxh7"))
        assertTrue(verified.brilliantPlan!!.contains("Ng5+"))
        assertFalse(verified.brilliantReason!!.contains("强制将杀"))
        val ordinary = analyzer.analyze(history, "d4c5", true)
        assertNotEquals(Grade.BRILLIANT, ordinary.grade)
        assertNull(ordinary.brilliantReason)
    }
}
