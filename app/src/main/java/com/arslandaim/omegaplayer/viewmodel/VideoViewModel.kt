/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.domain.usecase.media.GetVideosUseCase
import com.arslandaim.omegaplayer.domain.usecase.playback.GetRecentPlaybackUseCase
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import com.arslandaim.omegaplayer.media.PlaybackConnection
import com.arslandaim.omegaplayer.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    application: Application,
    private val getVideosUseCase: GetVideosUseCase,
    private val getRecentPlaybackUseCase: GetRecentPlaybackUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playbackConnection: PlaybackConnection
) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val activeVideoUri: StateFlow<String?> = playbackConnection.currentMediaItem
        .map { it?.localConfiguration?.uri?.toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isBackgroundPlayEnabled = MutableStateFlow(false)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    val isPlaying: StateFlow<Boolean> = playbackConnection.isPlaying

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    val videos: StateFlow<List<VideoModel>> = getVideosUseCase()
        .onStart { _isLoading.value = true }
        .onEach { 
            _isLoading.value = false
            preloadThumbnails(getApplication(), it)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPlayback: StateFlow<List<RecentPlayback>> = getRecentPlaybackUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _sleepTimerTimeLeft = MutableStateFlow(0L)
    val sleepTimerTimeLeft: StateFlow<Long> = _sleepTimerTimeLeft.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerActive.value = false
            _sleepTimerTimeLeft.value = 0
            return
        }

        _sleepTimerActive.value = true
        _sleepTimerTimeLeft.value = minutes * 60 * 1000L
        
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerTimeLeft.value > 0) {
                delay(1000)
                _sleepTimerTimeLeft.value -= 1000
            }
            _exoPlayer?.pause()
            _sleepTimerActive.value = false
        }
    }

    val folders: StateFlow<Map<String, Int>> = videos
        .map { videoList ->
            videoList.groupBy { File(it.path).parentFile?.name ?: "Internal" }
                .mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val videosInSelectedFolder: StateFlow<List<VideoModel>> = combine(videos, _selectedFolder) { videoList, folder ->
        if (folder == null) emptyList()
        else videoList.filter { (File(it.path).parentFile?.name ?: "Internal") == folder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var _exoPlayer: ExoPlayer? = null
    
    @androidx.annotation.OptIn(UnstableApi::class)
    fun getPlayer(context: Context): ExoPlayer {
        if (_exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 500, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            _exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build().apply {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying && _isBackgroundPlayEnabled.value) {
                                startPlaybackService(context)
                            }
                            if (!isPlaying) {
                                savePlaybackProgress()
                            }
                        }
                    })
                }
            PlaybackService.playerInstance = _exoPlayer
        }
        return _exoPlayer!!
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("VideoViewModel", "Failed to start PlaybackService", e)
        }
    }

    fun setActiveVideo() {
        // Automatically handled by PlaybackConnection
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun toggleBackgroundPlay(context: Context, enabled: Boolean) {
        _isBackgroundPlayEnabled.value = enabled
        if (!enabled) {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            context.applicationContext.stopService(intent)
        }
    }

    fun setSelectedFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun getCurrentVideo(): VideoModel? {
        val uri = activeVideoUri.value ?: return null
        return videos.value.find { it.uri.toString() == uri }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        // Professional approach: don't release if background play is enabled or service is active
        if (!_isBackgroundPlayEnabled.value) {
            _exoPlayer?.release()
            _exoPlayer = null
            PlaybackService.playerInstance = null
        }
    }

    fun stopIfPlaying(uri: Uri) {
        _exoPlayer?.let { player ->
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri == uri) {
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    fun stopIfPlaying(uris: List<Uri>) {
        _exoPlayer?.let { player ->
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != null && uris.contains(currentUri)) {
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    private fun preloadThumbnails(context: Context, videoList: List<VideoModel>) {
        val imageLoader = context.imageLoader
        viewModelScope.launch {
            videoList.forEach { video ->
                val request = ImageRequest.Builder(context)
                    .data(video.uri)
                    .videoFrameMillis(1000)
                    .size(400)
                    .precision(Precision.INEXACT)
                    .diskCacheKey("thumb_${video.id}")
                    .memoryCacheKey("thumb_${video.id}")
                    .build()
                imageLoader.enqueue(request)
                delay(50)
            }
        }
    }

    fun getVideosInFolder(folderName: String): List<VideoModel> {
        return videos.value.filter { (File(it.path).parentFile?.name ?: "Internal") == folderName }
    }

    fun fetchVideos(context: Context) {
        // Automatically handled by Flow
    }

    fun refreshVideos(context: Context) {
        // Trigger manual refresh if needed
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearVideoCache(context: Context, videoId: Long) {
        val imageLoader = context.imageLoader
        val key = "thumb_$videoId"
        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(key))
        imageLoader.diskCache?.remove(key)
    }

    fun savePlaybackProgress() {
        val player = _exoPlayer ?: return
        val mediaItem = player.currentMediaItem ?: return
        val currentUri = mediaItem.localConfiguration?.uri?.toString() ?: return
        val video = videos.value.find { it.uri.toString() == currentUri } ?: return
        
        val position = player.currentPosition
        val duration = player.duration
        val name = video.name

        viewModelScope.launch {
            playbackRepository.saveRecentPlayback(
                RecentPlayback(
                    uri = currentUri,
                    position = position,
                    duration = duration,
                    mediaType = "video",
                    name = name
                )
            )
        }
    }
}
