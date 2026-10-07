package cn.yibu.chess.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import cn.yibu.chess.core.GameRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "games")
data class StoredGame(@PrimaryKey val id: Long, val startedAt: Long, val payload: String)

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(game: StoredGame)
    @Query("SELECT * FROM games ORDER BY startedAt DESC") fun observe(): Flow<List<StoredGame>>
    @Query("SELECT * FROM games ORDER BY startedAt DESC LIMIT 1") suspend fun latest(): StoredGame?
}

@Database(entities = [StoredGame::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    companion object {
        @Volatile private var instance: GameDatabase? = null
        fun get(context: Context): GameDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GameDatabase::class.java, "yibu-chess.db")
                .build().also { instance = it }
        }
    }
}

class GameRepository(context: Context) {
    private val dao = GameDatabase.get(context).games()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val games = dao.observe().map { rows -> rows.mapNotNull { runCatching { json.decodeFromString<GameRecord>(it.payload) }.getOrNull() } }
    suspend fun latest(): GameRecord? = dao.latest()?.let { runCatching { json.decodeFromString<GameRecord>(it.payload) }.getOrNull() }
    suspend fun save(game: GameRecord) { dao.save(StoredGame(game.id, game.startedAt, json.encodeToString(game))) }
    fun diagnostics(game: GameRecord): String = json.encodeToString(game)
}
