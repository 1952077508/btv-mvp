package com.btv.mvp.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "app_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "message") val message: String
)

@Entity(tableName = "room_history")
data class RoomHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "room_id") val roomId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: Long
)

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<LogEntity>

    @Query("SELECT COUNT(*) FROM app_logs")
    suspend fun count(): Int

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int = 500)

    @Query("DELETE FROM app_logs")
    suspend fun clearAll()
}

@Dao
interface RoomHistoryDao {
    @Insert
    suspend fun insert(entry: RoomHistoryEntity)

    @Query("SELECT * FROM room_history ORDER BY joined_at DESC LIMIT 30")
    suspend fun getAll(): List<RoomHistoryEntity>

    @Query("DELETE FROM room_history WHERE room_id = :roomId")
    suspend fun deleteByRoomId(roomId: String)

    @Query("DELETE FROM room_history")
    suspend fun clearAll()
}

@Database(entities = [LogEntity::class, RoomHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun roomHistoryDao(): RoomHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "btv_logs.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
