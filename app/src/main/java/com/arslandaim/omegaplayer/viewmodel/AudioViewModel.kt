/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.PlaylistItem
import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.data.repository.PlaybackRepository
import com.arslandaim.omegaplayer.data.ThemePreferences
import com.arslandaim.omegaplayer.domain.usecase.media.GetAudiosUseCase
import com.arslandaim.omegaplayer.domain.usecase.media.GetVideosUseCase
import com.arslandaim.omegaplayer.domain.usecase.playback.GetRecentPlaybackUseCase
import com.arslandaim.omegaplayer.domain.usecase.playback.PlaylistUseCases
import com.arslandaim.omegaplayer.media.PlaybackConnection
import com.arslandaim.omegaplayer.util.Resource
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(
    application: Application,
    private val getAudiosUseCase: GetAudiosUseCase,
    private val playlistUseCases: PlaylistUseCases,
    private val getRecentPlaybackUseCase: GetRecentPlaybackUseCase,
    private val playbackConnection: PlaybackConnection,
    private val playbackRepository: PlaybackRepository,
    private val themePreferences: ThemePreferences
) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    val activeAudioUri: StateFlow<String?> = playbackConnection.currentMediaItem
        .map { it?.localConfiguration?.uri?.toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = playbackConnection.isPlaying
    val mediaController: StateFlow<MediaController?> = playbackConnection.mediaController

    private val _audioError = MutableStateFlow<String?>(null)
    val audioError: StateFlow<String?> = _audioError.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val audios: StateFlow<List<AudioModel>> = refreshTrigger
        .flatMapLatest { getAudiosUseCase() }
        .onEach { resource ->
            when (resource) {
                is Resource.Loading -> _isLoading.value = true
                is Resource.Success -> {
                    _isLoading.value = false
                    _audioError.value = null
                }
                is Resource.Error -> {
                    _isLoading.value = false
                    _audioError.value = resource.message
                }
            }
        }
        .map { resource -> if (resource is Resource.Success) resource.data else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistUseCases.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPlayback: StateFlow<List<RecentPlayback>> = getRecentPlaybackUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isHistoryPaused: StateFlow<Boolean> = themePreferences.isHistoryPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()

    private val _sleepTimerTimeLeft = MutableStateFlow(0L)
    val sleepTimerTimeLeft: StateFlow<Long> = _sleepTimerTimeLeft.asStateFlow()

    private val _stopAfterCurrent = MutableStateFlow(false)
    val stopAfterCurrent: StateFlow<Boolean> = _stopAfterCurrent.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _stopAfterCurrent.value = false
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
            playbackConnection.pause()
            _sleepTimerActive.value = false
        }
    }

    fun setStopAfterCurrent(enabled: Boolean) {
        _stopAfterCurrent.value = enabled
        if (enabled) {
            _sleepTimerActive.value = true
            _sleepTimerTimeLeft.value = 0
            sleepTimerJob?.cancel()
            
            // Wire up listener to stop when media item changes
            val controller = playbackConnection.mediaController.value ?: return
            controller.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _stopAfterCurrent.value) {
                        controller.pause()
                        _stopAfterCurrent.value = false
                        _sleepTimerActive.value = false
                        controller.removeListener(this)
                    }
                }
            })
        } else {
            _sleepTimerActive.value = false
        }
    }

    val folders: StateFlow<Map<String, Int>> = audios
        .map { audioList ->
            audioList.groupBy { File(it.path).parentFile?.name ?: "Internal" }
                .mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val audiosInSelectedFolder: StateFlow<List<AudioModel>> = combine(audios, _selectedFolder) { audioList, folder ->
        if (folder == null) emptyList()
        else audioList.filter { (File(it.path).parentFile?.name ?: "Internal") == folder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistUseCases.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistUseCases.deletePlaylist(playlist)
        }
    }

    fun addToPlaylist(playlistId: Int, uri: String, type: String) {
        viewModelScope.launch {
            playlistUseCases.addToPlaylist(playlistId, uri, type)
        }
    }

    fun removeFromPlaylist(playlistId: Int, uri: String) {
        viewModelScope.launch {
            playlistUseCases.removeFromPlaylist(playlistId, uri)
        }
    }

    fun getPlaylistItems(playlistId: Int): Flow<List<PlaylistItem>> {
        return playlistUseCases.getPlaylistItems(playlistId)
    }

    fun togglePlayPause(audio: AudioModel) {
        val controller = playbackConnection.mediaController.value ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
        
        if (currentUri == audio.uri.toString()) {
            if (controller.isPlaying) controller.pause() else controller.play()
        } else {
            val folderAudios = audiosInSelectedFolder.value
            val mediaItems = folderAudios.map { audioItem ->
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
            val index = folderAudios.indexOfFirst { it.id == audio.id }.coerceAtLeast(0)
            
            if (mediaItems.isNotEmpty()) {
                controller.setMediaItems(mediaItems, index, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun setSelectedFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun stopIfPlaying(uri: Uri) {
        val controller = playbackConnection.mediaController.value ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri
        if (currentUri == uri) {
            controller.stop()
            controller.clearMediaItems()
        }
    }

    fun stopIfPlaying(uris: List<Uri>) {
        val controller = playbackConnection.mediaController.value ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri
        if (currentUri != null && uris.contains(currentUri)) {
            controller.stop()
            controller.clearMediaItems()
        }
    }

    fun getAudiosInFolder(folderName: String): List<AudioModel> {
        return audios.value.filter { (File(it.path).parentFile?.name ?: "Internal") == folderName }
    }

    fun fetchAudios(context: Context) {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    fun refreshAudios(context: Context) {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    fun savePlaybackProgress() {
        val controller = playbackConnection.mediaController.value ?: return
        val mediaItem = controller.currentMediaItem ?: return
        val currentUri = mediaItem.localConfiguration?.uri?.toString() ?: return
        val audio = audios.value.find { it.uri.toString() == currentUri } ?: return
        
        val position = controller.currentPosition
        val duration = controller.duration
        val name = audio.name
        val artist = audio.artist

        viewModelScope.launch(Dispatchers.IO) {
            if (themePreferences.isHistoryPaused.first()) return@launch
            
            playbackRepository.saveRecentPlayback(
                RecentPlayback(
                    uri = currentUri,
                    position = position,
                    duration = duration,
                    mediaType = "audio",
                    name = name,
                    artist = artist
                )
            )
        }
    }
}
