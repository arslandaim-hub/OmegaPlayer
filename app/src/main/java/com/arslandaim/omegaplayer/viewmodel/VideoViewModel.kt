/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.service.PlaybackService
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    private val _videos = MutableStateFlow<List<VideoModel>>(emptyList())
    val videos: StateFlow<List<VideoModel>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeVideoUri = MutableStateFlow<String?>(null)
    val activeVideoUri: StateFlow<String?> = _activeVideoUri.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(false)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    val folders: StateFlow<Map<String, Int>> = _videos
        .map { videoList ->
            videoList.groupBy { File(it.path).parentFile?.name ?: "Internal" }
                .mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val videosInSelectedFolder: StateFlow<List<VideoModel>> = combine(_videos, _selectedFolder) { videoList, folder ->
        if (folder == null) emptyList()
        else videoList.filter { (File(it.path).parentFile?.name ?: "Internal") == folder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var contentObserver: ContentObserver? = null
    
    private var _exoPlayer: ExoPlayer? = null
    
    @androidx.annotation.OptIn(UnstableApi::class)
    fun getPlayer(context: Context): ExoPlayer {
        if (_exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            
            // Optimized for instant startup and silky smooth playback of large files
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // minBufferMs: Reduced to 15s for faster initial load
                    50_000, // maxBufferMs: 50s for solid background buffering
                    500,    // bufferForPlaybackMs: Extremely low (500ms) for near-instant start
                    1_000   // bufferForPlaybackAfterRebufferMs: 1s for quick recovery
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            _exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build().apply {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC) // Faster seeking by snapping to keyframes
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            
                            if (isPlaying && _isBackgroundPlayEnabled.value) {
                                startPlaybackService(context)
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                _isPlaying.value = false
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

    @androidx.annotation.OptIn(UnstableApi::class)
    fun stopPlaybackService(context: Context) {
        // No-op. Media3 manages service lifecycle.
    }

    fun setActiveVideo(uri: String?) {
        _activeVideoUri.value = uri
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
        val uri = _activeVideoUri.value ?: return null
        return _videos.value.find { it.uri.toString() == uri }
    }

    fun fetchVideos(context: Context) {
        // Start observing if not already
        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d("VideoViewModel", "MediaStore changed, refreshing: $uri")
                    refreshVideos(context)
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        }

        if (_videos.value.isNotEmpty() && !_isLoading.value) return
        
        fetchVideosInternal(context)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
        PlaybackService.playerInstance = null
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    fun refreshVideos(context: Context) {
        fetchVideosInternal(context)
    }

    private fun fetchVideosInternal(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val videoList = withContext(Dispatchers.IO) {
                val list = mutableListOf<VideoModel>()
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATA
                )

                try {
                    val cursor = context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${MediaStore.Video.Media.DATE_ADDED} DESC"
                    )

                    cursor?.use {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            val name = it.getString(nameColumn) ?: "Unknown"
                            val duration = it.getLong(durationColumn)
                            val size = it.getLong(sizeColumn)
                            val path = it.getString(dataColumn) ?: ""
                            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                            list.add(VideoModel(id, contentUri, name, duration, size, path))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoViewModel", "Error fetching videos", e)
                }
                list
            }

            // Clear cache for removed videos
            val currentVideos = _videos.value
            val newVideoIds = videoList.map { it.id }.toSet()
            currentVideos.forEach { oldVideo ->
                if (oldVideo.id !in newVideoIds) {
                    clearVideoCache(context, oldVideo.id)
                }
            }

            _videos.value = videoList
            _isLoading.value = false
            preloadThumbnails(context, videoList)
        }
    }

    private fun preloadThumbnails(context: Context, videoList: List<VideoModel>) {
        val imageLoader = context.imageLoader
        viewModelScope.launch {
            // Preload thumbnails with a slight delay between each to avoid IO saturation
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
                delay(50) // 50ms gap to keep the UI thread and IO pipeline smooth
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearVideoCache(context: Context, videoId: Long) {
        val imageLoader = context.imageLoader
        val key = "thumb_$videoId"
        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(key))
        imageLoader.diskCache?.remove(key)
    }

    fun getVideosInFolder(folderName: String): List<VideoModel> {
        return _videos.value.filter { (File(it.path).parentFile?.name ?: "Internal") == folderName }
    }
}
