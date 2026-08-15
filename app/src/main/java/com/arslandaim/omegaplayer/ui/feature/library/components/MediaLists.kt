package com.arslandaim.omegaplayer.ui.feature.library.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.PlaylistItem
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaListItemInPlaylist(
    item: PlaylistItem,
    videos: List<VideoModel>,
    audios: List<AudioModel>,
    videoViewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    playlist: Playlist,
    onVideoLock: (VideoModel) -> Unit,
    onAudioLock: (AudioModel) -> Unit,
    onVideoDelete: (VideoModel) -> Unit,
    onAudioDelete: (AudioModel) -> Unit
) {
    if (item.mediaType == "video") {
        val video = videos.find { it.uri.toString() == item.mediaUri }
        if (video != null) {
            VideoListItem(
                video = video,
                isPlaying = videoViewModel.activeVideoUri.collectAsState().value == video.uri.toString() && videoViewModel.isPlaying.collectAsState().value,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = {
                    val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString())
                    onVideoClick(encodedUri)
                },
                onLockClick = { onVideoLock(video) },
                onDeleteClick = { onVideoDelete(video) },
                onPlaylistClick = { audioViewModel.removeFromPlaylist(playlist.id, video.uri.toString()) },
                isInPlaylistView = true
            )
        }
    } else {
        val audio = audios.find { it.uri.toString() == item.mediaUri }
        if (audio != null) {
            AudioListItem(
                audio = audio,
                isPlaying = audioViewModel.activeAudioUri.collectAsState().value == audio.uri.toString() && audioViewModel.isPlaying.collectAsState().value,
                onClick = {
                    val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString())
                    onAudioClick(encodedUri)
                },
                onPlayPauseClick = { audioViewModel.togglePlayPause(audio) },
                onLockClick = { onAudioLock(audio) },
                onDeleteClick = { onAudioDelete(audio) },
                onPlaylistClick = { audioViewModel.removeFromPlaylist(playlist.id, audio.uri.toString()) },
                isInPlaylistView = true
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlaylistGridItem(
    item: PlaylistItem,
    videos: List<VideoModel>,
    audios: List<AudioModel>,
    videoViewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    playlist: Playlist
) {
    if (item.mediaType == "video") {
        val video = videos.find { it.uri.toString() == item.mediaUri }
        if (video != null) {
            VideoGridItem(video, videoViewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, {}, {}, {})
        }
    } else {
        val audio = audios.find { it.uri.toString() == item.mediaUri }
        if (audio != null) {
            AudioGridItem(audio, audioViewModel, onAudioClick, {}, {}, {})
        }
    }
}

@Composable
fun EmptyState(isSearching: Boolean, isFolderView: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.SearchOff else if (isFolderView) Icons.Default.FolderOff else Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isSearching) "No results found" else if (isFolderView) "No folders found" else "No media items",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isSearching) "Try a different search term" else "Your media library is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddToPlaylistFromHomeDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Playlist")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (playlists.isEmpty()) {
                    Text("No playlists yet", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                modifier = Modifier.clickable { onPlaylistSelected(playlist.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
