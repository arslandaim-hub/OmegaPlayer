package com.arslandaim.omegaplayer.media

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arslandaim.omegaplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @androidx.annotation.OptIn(UnstableApi::class)
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    @androidx.annotation.OptIn(UnstableApi::class)
    val mediaController = _mediaController.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem = _currentMediaItem.asStateFlow()

    @androidx.annotation.OptIn(UnstableApi::class)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        initializeController()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _mediaController.value = controller
                _isPlaying.value = controller.isPlaying
                _currentMediaItem.value = controller.currentMediaItem
                
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentMediaItem.value = mediaItem
                    }
                })
            } catch (e: Exception) {
                Log.e("PlaybackConnection", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    fun play() = _mediaController.value?.play()
    fun pause() = _mediaController.value?.pause()
    fun stop() = _mediaController.value?.stop()
}
