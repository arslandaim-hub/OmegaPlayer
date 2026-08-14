package com.arslandaim.omegaplayer.data.repository

import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.PlaylistItem
import com.arslandaim.omegaplayer.data.RecentPlayback
import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(playlist: Playlist)
    fun getPlaylistItems(playlistId: Int): Flow<List<PlaylistItem>>
    suspend fun addToPlaylist(playlistId: Int, uri: String, type: String)
    suspend fun removeFromPlaylist(playlistId: Int, uri: String)
    
    fun getRecentPlayback(): Flow<List<RecentPlayback>>
    fun getAllRecentPlayback(): Flow<List<RecentPlayback>>
    suspend fun saveRecentPlayback(recent: RecentPlayback)
    suspend fun clearAllRecentPlayback()
}
