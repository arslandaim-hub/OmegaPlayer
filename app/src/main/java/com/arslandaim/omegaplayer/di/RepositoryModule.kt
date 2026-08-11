package com.arslandaim.omegaplayer.di

import com.arslandaim.omegaplayer.data.repository.MediaRepository
import com.arslandaim.omegaplayer.data.repository.MediaRepositoryImpl
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import com.arslandaim.omegaplayer.data.repository.PlaybackRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        mediaRepositoryImpl: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        playbackRepositoryImpl: PlaybackRepositoryImpl
    ): PlaybackRepository
}
