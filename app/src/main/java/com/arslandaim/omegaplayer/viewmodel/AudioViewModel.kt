/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.service.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val _audios = MutableStateFlow<List<AudioModel>>(emptyList())
    val audios: StateFlow<List<AudioModel>> = _audios.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _activeAudioUri = MutableStateFlow<String?>(null)
    val activeAudioUri: StateFlow<String?> = _activeAudioUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    val folders: StateFlow<Map<String, Int>> = _audios
        .map { audioList ->
            audioList.groupBy { File(it.path).parentFile?.name ?: "Internal" }
                .mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val audiosInSelectedFolder: StateFlow<List<AudioModel>> = combine(_audios, _selectedFolder) { audioList, folder ->
        if (folder == null) emptyList()
        else audioList.filter { (File(it.path).parentFile?.name ?: "Internal") == folder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var contentObserver: ContentObserver? = null

    init {
        initializeController()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                _mediaController.value = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED ||
                            playbackState == Player.STATE_IDLE
                        ) {
                            _isPlaying.value = false
                        }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _activeAudioUri.value = mediaItem?.localConfiguration?.uri?.toString()
                    }
                },
            )
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    fun togglePlayPause(audio: AudioModel) {
        val controller = _mediaController.value ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
        
        if (currentUri == audio.uri.toString()) {
            if (controller.isPlaying) controller.pause() else controller.play()
        } else {
            val allAudios = _audios.value
            val mediaItems = allAudios.map { audioItem ->
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    audioItem.albumId
                )
                MediaItem.Builder()
                    .setUri(audioItem.uri)
                    .setMediaId(audioItem.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(audioItem.name)
                            .setArtist(audioItem.artist)
                            .setAlbumTitle(audioItem.album)
                            .setArtworkUri(albumArtUri)
                            .build()
                    )
                    .build()
            }
            val index = allAudios.indexOfFirst { it.id == audio.id }.coerceAtLeast(0)
            
            if (mediaItems.isNotEmpty()) {
                controller.setMediaItems(mediaItems, index, 0L)
                controller.prepare()
                controller.play()
                _activeAudioUri.value = audio.uri.toString()
            }
        }
    }

    fun setActiveAudio(uri: String?) {
        _activeAudioUri.value = uri
    }

    fun setSelectedFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun fetchAudios(context: Context) {
        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    refreshAudios(context)
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        }

        if (_audios.value.isNotEmpty() && !_isLoading.value) return
        fetchAudiosInternal(context)
    }

    fun refreshAudios(context: Context) {
        fetchAudiosInternal(context)
    }

    private fun fetchAudiosInternal(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val audioList = withContext(Dispatchers.IO) {
                val list = mutableListOf<AudioModel>()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATA
                )

                try {
                    val cursor = context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                    )

                    cursor?.use {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                        val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                        while (it.moveToNext()) {
                            val id = it.getLong(idColumn)
                            val albumId = it.getLong(albumIdColumn)
                            val name = it.getString(nameColumn) ?: "Unknown"
                            val artist = it.getString(artistColumn) ?: "Unknown Artist"
                            val album = it.getString(albumColumn) ?: "Unknown Album"
                            val duration = it.getLong(durationColumn)
                            val size = it.getLong(sizeColumn)
                            val path = it.getString(dataColumn) ?: ""
                            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                            list.add(AudioModel(id, albumId, contentUri, name, artist, album, duration, size, path))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AudioViewModel", "Error fetching audios", e)
                }
                list
            }
            _audios.value = audioList
            _isLoading.value = false
        }
    }

    fun getAudiosInFolder(folderName: String): List<AudioModel> {
        return _audios.value.filter { (File(it.path).parentFile?.name ?: "Internal") == folderName }
    }

    override fun onCleared() {
        super.onCleared()
        _mediaController.value?.release()
        _mediaController.value = null
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }
}
