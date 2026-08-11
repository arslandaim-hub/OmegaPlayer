package com.arslandaim.omegaplayer.domain.usecase.playback

import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPlaylistsUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    operator fun invoke(): Flow<List<Playlist>> = repository.getPlaylists()
}
