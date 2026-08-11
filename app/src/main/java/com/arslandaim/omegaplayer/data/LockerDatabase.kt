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

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val mediaUri: String,
    val mediaType: String, // "video" or "audio"
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_playback")
data class RecentPlayback(
    @PrimaryKey val uri: String,
    val position: Long,
    val duration: Long,
    val lastPlayed: Long = System.currentTimeMillis(),
    val mediaType: String,
    val name: String,
    val artist: String? = null
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

    // Playlist Methods
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getPlaylistItemsFlow(playlistId: Int): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItem)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun removePlaylistItem(playlistId: Int, mediaUri: String)

    // Recent Playback Methods
    @Query("SELECT * FROM recent_playback ORDER BY lastPlayed DESC LIMIT 20")
    fun getRecentPlaybackFlow(): Flow<List<RecentPlayback>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentPlayback(recent: RecentPlayback)

    @Query("SELECT * FROM recent_playback WHERE uri = :uri")
    suspend fun getRecentPlayback(uri: String): RecentPlayback?

    @Query("DELETE FROM recent_playback WHERE uri = :uri")
    suspend fun deleteRecentPlayback(uri: String)
}

@Database(entities = [LockedVideo::class, LockerSettings::class, Playlist::class, PlaylistItem::class, RecentPlayback::class], version = 6)
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
