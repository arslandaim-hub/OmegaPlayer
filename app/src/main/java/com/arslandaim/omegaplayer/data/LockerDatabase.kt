/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "locked_videos")
data class LockedVideo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalPath: String,
    val lockerPath: String,
    val name: String,
    val duration: Long,
    val originFolderName: String? = null,
    val isAudio: Boolean = false
)

@Entity(tableName = "locker_settings")
data class LockerSettings(
    @PrimaryKey val id: Int = 1,
    val pin: String,
    val securityQuestion: String,
    val securityAnswer: String,
    val isBiometricEnabled: Boolean = false
)

@Dao
interface LockerDao {
    @Query("SELECT * FROM locked_videos")
    suspend fun getAllLockedVideos(): List<LockedVideo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockedVideo(video: LockedVideo)

    @Delete
    suspend fun deleteLockedVideo(video: LockedVideo)

    @Query("SELECT * FROM locker_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<LockerSettings?>

    @Query("SELECT * FROM locker_settings WHERE id = 1")
    suspend fun getSettings(): LockerSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: LockerSettings)
}

@Database(entities = [LockedVideo::class, LockerSettings::class], version = 5)
abstract class LockerDatabase : RoomDatabase() {
    abstract fun lockerDao(): LockerDao

    companion object {
        @Volatile
        private var INSTANCE: LockerDatabase? = null

        fun getDatabase(context: Context): LockerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LockerDatabase::class.java,
                    "locker_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
