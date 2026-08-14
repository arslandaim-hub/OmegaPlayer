package com.arslandaim.omegaplayer.data.repository

import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.util.Resource
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAudios(): Flow<Resource<List<AudioModel>>>
    fun getVideos(): Flow<Resource<List<VideoModel>>>
    suspend fun refreshMedia()
}
