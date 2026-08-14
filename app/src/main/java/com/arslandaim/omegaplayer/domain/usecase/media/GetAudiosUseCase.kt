package com.arslandaim.omegaplayer.domain.usecase.media

import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.repository.MediaRepository
import com.arslandaim.omegaplayer.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAudiosUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(): Flow<Resource<List<AudioModel>>> = repository.getAudios()
}
