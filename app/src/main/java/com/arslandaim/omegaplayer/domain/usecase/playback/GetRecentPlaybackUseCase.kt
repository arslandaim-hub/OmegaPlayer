package com.arslandaim.omegaplayer.domain.usecase.playback

import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentPlaybackUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    operator fun invoke(): Flow<List<RecentPlayback>> = repository.getRecentPlayback()
}
