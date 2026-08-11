package com.arslandaim.omegaplayer.domain.usecase.playback

import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import javax.inject.Inject

class PlaylistUseCases @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend fun createPlaylist(name: String) = repository.createPlaylist(name)
    suspend fun deletePlaylist(playlist: Playlist) = repository.deletePlaylist(playlist)
    fun getPlaylists() = repository.getPlaylists()
    fun getPlaylistItems(playlistId: Int) = repository.getPlaylistItems(playlistId)
    suspend fun addToPlaylist(playlistId: Int, uri: String, type: String) = 
        repository.addToPlaylist(playlistId, uri, type)
    suspend fun removeFromPlaylist(playlistId: Int, uri: String) = 
        repository.removeFromPlaylist(playlistId, uri)
}
