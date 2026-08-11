package com.arslandaim.omegaplayer.domain.usecase.media

import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideosUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(): Flow<List<VideoModel>> = repository.getVideos()
}
