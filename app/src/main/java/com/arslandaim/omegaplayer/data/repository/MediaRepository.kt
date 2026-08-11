package com.arslandaim.omegaplayer.data.repository

import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.VideoModel
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAudios(): Flow<List<AudioModel>>
    fun getVideos(): Flow<List<VideoModel>>
    suspend fun refreshMedia()
}
