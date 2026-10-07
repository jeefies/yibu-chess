package cn.yibu.chess.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import cn.yibu.chess.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRepositoryTest {
    private lateinit var application: Application
    private lateinit var database: GameDatabase
    private lateinit var repository: GameRepository
    @Before fun setUp() {
        application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, GameDatabase::class.java).build()
        repository = GameRepository(application, database)
    }
    @After fun tearDown() { database.close() }
    private fun finished(result: String, humanWhite: Boolean = true, mode: Difficulty = Difficulty.MATCHED) =
        EloRules.newGame(PlayerProfile(), mode, humanWhite).copy(result = result, finished = true)

    @Test fun concurrentSavesAndReviewingCannotDoubleCountAWin() = runBlocking {
        val game = finished("1-0")
        coroutineScope { List(8) { async(Dispatchers.IO) { repository.save(game) } }.awaitAll() }
        assertEquals(PlayerProfile(532, 1), repository.profile())
        assertEquals(32, repository.latest()?.ratingChange?.delta)
        repository.save(game.copy(ending = "复盘说明更新"))
        assertEquals(PlayerProfile(532, 1), repository.profile())
        assertEquals(532, repository.latest()?.ratingChange?.after)
    }
    @Test fun lossesAndDrawsSettleFromTheHumanPerspective() = runBlocking {
        val blackWins = finished("0-1", humanWhite = false)
        repository.save(blackWins)
        val draw = EloRules.newGame(repository.profile(), Difficulty.MATCHED, true).copy(result = "1/2-1/2", finished = true)
        repository.save(draw)
        assertEquals(PlayerProfile(532, 2), repository.profile())
        val loss = EloRules.newGame(repository.profile(), Difficulty.MATCHED, false).copy(result = "1-0", finished = true)
        val saved = repository.save(loss)
        assertEquals(PlayerProfile(500, 3), saved.profile)
        assertEquals(-32, saved.game?.ratingChange?.delta)
    }
    @Test fun strongAndOlderGamesAndUnfinishedMatchesDoNotChangeRating() = runBlocking {
        repository.save(finished("1-0", mode = Difficulty.STRONG))
        repository.save(GameRecord(difficulty = Difficulty.LIGHT, finished = true, result = "0-1"))
        repository.save(EloRules.newGame(PlayerProfile(), Difficulty.MATCHED, true))
        assertEquals(PlayerProfile(), repository.profile())
        assertEquals(3, repository.games.first().size)
        assertTrue(repository.games.first().all { it.ratingChange == null })
    }
    @Test fun deletionKeepsSettledRatingAndDelayedWritesCannotRestoreTheGame() = runBlocking {
        val game = finished("0-1")
        repository.save(game)
        repository.delete(game.id)
        assertTrue(repository.games.first().isEmpty())
        val delayed = repository.save(game)
        assertNull(delayed.game)
        assertTrue(repository.games.first().isEmpty())
        assertEquals(PlayerProfile(468, 1), repository.profile())
        // Even an unfinished record deleted during analysis must stay deleted.
        val unfinished = EloRules.newGame(repository.profile(), Difficulty.MATCHED, true)
        repository.save(unfinished)
        repository.delete(unfinished.id)
        repository.save(unfinished.copy(finished = true, result = "1-0"))
        assertTrue(repository.games.first().isEmpty())
        assertEquals(PlayerProfile(468, 1), repository.profile())
    }
    @Test fun aDelayedUnfinishedSaveCannotUndoTheResultOrRating() = runBlocking {
        val pending = EloRules.newGame(PlayerProfile(), Difficulty.MATCHED, true).copy(moves = listOf("e2e4"))
        repository.save(pending.copy(finished = true, result = "0-1", ending = "认输"))
        repository.save(pending)
        val restored = requireNotNull(repository.latest())
        assertTrue(restored.finished)
        assertEquals("0-1", restored.result)
        assertEquals(-32, restored.ratingChange?.delta)
        assertEquals(PlayerProfile(468, 1), repository.profile())
    }
    @Test fun migrationKeepsVersionOnePayloadAndCreatesIndependentInitialRating() = runBlocking {
        database.close()
        val name = "migration-test.db"
        application.deleteDatabase(name)
        val path = application.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE games (id INTEGER NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
            old.execSQL("INSERT INTO games (id, startedAt, payload) VALUES (?, ?, ?)", arrayOf(123L, 456L,
                """{"id":123,"startedAt":456,"difficulty":"RELAXED","moves":["e2e4"],"finished":true,"result":"0-1"}"""))
            old.version = 1
        }
        database = Room.databaseBuilder(application, GameDatabase::class.java, name).addMigrations(GameDatabase.MIGRATION_1_2).build()
        repository = GameRepository(application, database)
        val old = requireNotNull(repository.latest())
        assertEquals(listOf("e2e4"), old.moves)
        assertFalse(old.rated)
        assertEquals(PlayerProfile(), repository.profile())
        repository.save(old)
        assertEquals(PlayerProfile(), repository.profile())
        repository.save(EloRules.newGame(repository.profile(), Difficulty.MATCHED, true).copy(finished = true, result = "1-0"))
        assertEquals(PlayerProfile(532, 1), repository.profile())
        assertEquals(2, repository.games.first().size)
    }
}
