package com.arslandaim.omegaplayer.data.repository

import com.arslandaim.omegaplayer.data.LockerDao
import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.PlaylistItem
import com.arslandaim.omegaplayer.data.RecentPlayback
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val lockerDao: LockerDao
) : PlaybackRepository {

    override fun getPlaylists(): Flow<List<Playlist>> = lockerDao.getAllPlaylistsFlow()

    override suspend fun createPlaylist(name: String) {
        lockerDao.insertPlaylist(Playlist(name = name))
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        lockerDao.deletePlaylist(playlist)
    }

    override fun getPlaylistItems(playlistId: Int): Flow<List<PlaylistItem>> {
        return lockerDao.getPlaylistItemsFlow(playlistId)
    }

    override suspend fun addToPlaylist(playlistId: Int, uri: String, type: String) {
        lockerDao.insertPlaylistItem(PlaylistItem(playlistId = playlistId, mediaUri = uri, mediaType = type))
    }

    override suspend fun removeFromPlaylist(playlistId: Int, uri: String) {
        lockerDao.removePlaylistItem(playlistId, uri)
    }

    override fun getRecentPlayback(): Flow<List<RecentPlayback>> = lockerDao.getRecentPlaybackFlow()

    override fun getAllRecentPlayback(): Flow<List<RecentPlayback>> = lockerDao.getAllRecentPlaybackFlow()

    override suspend fun saveRecentPlayback(recent: RecentPlayback) {
        lockerDao.insertRecentPlayback(recent)
    }

    override suspend fun clearAllRecentPlayback() {
        lockerDao.clearAllRecentPlayback()
    }
}
