package dev.gestrin.hub.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY lastOpened DESC, title COLLATE NOCASE, id")
    fun observeAll(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE favorite = 1 ORDER BY title COLLATE NOCASE, id")
    fun observeFavorites(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE platform = :platform ORDER BY title COLLATE NOCASE, id")
    fun observePlatform(platform: String): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Game?>

    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE, id")
    suspend fun getAll(): List<Game>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Game?

    @Query("SELECT * FROM games WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): Game?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(game: Game): Long

    /** Import-only: IGNORE preserves existing titles, favorites, covers and sessions. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(game: Game): Long

    @Update
    suspend fun update(game: Game): Int

    @Delete
    suspend fun delete(game: Game): Int

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE games SET lastOpened = MAX(lastOpened, :time) WHERE id = :id")
    suspend fun markOpened(id: String, time: Long): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC, id")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE gameId = :gameId ORDER BY startedAt DESC, id")
    fun observeForGame(gameId: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Session?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session): Int

    @Query("UPDATE sessions SET endedAt = MAX(startedAt, :time), note = :note WHERE id = :id AND endedAt IS NULL")
    suspend fun finish(id: String, time: Long, note: String?): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Database(entities = [Game::class, Session::class], version = 1, exportSchema = true)
abstract class HubDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: HubDatabase? = null

        fun getInstance(context: Context): HubDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, HubDatabase::class.java, "gestrin-hub.db"
            ).build().also { instance = it }
        }
    }
}
