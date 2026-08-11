/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import kotlinx.coroutines.delay
import android.content.ContentUris
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.BackHandler

import android.media.audiofx.Equalizer
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import com.arslandaim.omegaplayer.data.Playlist
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import com.arslandaim.omegaplayer.data.AudioModel

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableIntStateOf(if (currentMinutes > 0) currentMinutes else 30) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${minutes} minutes")
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = 0f..120f,
                    steps = 23 // 5 min increments if 0-120
                )
                if (currentMinutes > 0) {
                    TextButton(onClick = { onConfirm(0) }) {
                        Text("Turn Off", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(minutes) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
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
                                leadingContent = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    audioUri: String,
    viewModel: AudioViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val controller by viewModel.mediaController.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepTimerActive by viewModel.sleepTimerActive.collectAsStateWithLifecycle()
    val sleepTimerTimeLeft by viewModel.sleepTimerTimeLeft.collectAsStateWithLifecycle()
    val globalAudios by viewModel.audios.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val audios by viewModel.audiosInSelectedFolder.collectAsStateWithLifecycle()
    
    var currentAudio by remember { mutableStateOf(globalAudios.find { it.uri.toString() == audioUri }) }

    LaunchedEffect(globalAudios, audioUri) {
        if (selectedFolder == null && globalAudios.isNotEmpty()) {
            val audio = globalAudios.find { it.uri.toString() == audioUri }
            audio?.let {
                val folderName = java.io.File(it.path).parentFile?.name ?: "Internal"
                viewModel.setSelectedFolder(folderName)
            }
        }
        if (currentAudio == null) {
            currentAudio = globalAudios.find { it.uri.toString() == audioUri }
        }
    }

    var isPlaying by remember { mutableStateOf(controller?.isPlaying ?: false) }
    var currentPosition by remember { mutableStateOf(controller?.currentPosition ?: 0L) }
    var duration by remember { mutableStateOf(controller?.duration?.coerceAtLeast(0L) ?: 0L) }
    var repeatMode by remember { mutableStateOf(controller?.repeatMode ?: Player.REPEAT_MODE_OFF) }
    
    var dominantColor by remember { mutableStateOf(Color(0xFF1A1A1A)) }
    val animatedBgColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "bgColor"
    )

    val albumArtUri = remember(currentAudio) {
        currentAudio?.let {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                it.albumId
            )
        }
    }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(albumArtUri)
                .allowHardware(false) // Palette needs software bitmap
                .build()
            
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(0xFF1A1A1A.toInt())
                        )
                    )
                    dominantColor = Color(color).copy(alpha = 0.6f)
                }
            }
        } else {
            dominantColor = Color(0xFF1A1A1A)
        }
    }

    LaunchedEffect(controller, audios) {
        val player = controller ?: return@LaunchedEffect
        
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val currentUri = mediaItem?.localConfiguration?.uri?.toString()
                currentAudio = audios.find { it.uri.toString() == currentUri }
                viewModel.setActiveAudio(currentUri)
            }
        }
        player.addListener(listener)
        
        // Initial state
        isPlaying = player.isPlaying
        duration = player.duration.coerceAtLeast(0L)
        repeatMode = player.repeatMode
        
        // Only set playlist and play if it's not already playing this specific URI or if it's a new session
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != audioUri) {
            val mediaItems = audios.map { audioItem ->
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    audioItem.albumId
                )
                androidx.media3.common.MediaItem.Builder()
                    .setUri(audioItem.uri)
                    .setMediaId(audioItem.id.toString())
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(audioItem.name)
                            .setArtist(audioItem.artist)
                            .setAlbumTitle(audioItem.album)
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .build()
            }
            val index = audios.indexOfFirst { it.uri.toString() == audioUri }.coerceAtLeast(0)
            
            if (mediaItems.isNotEmpty()) {
                player.setMediaItems(mediaItems, index, 0L)
                player.prepare()
                player.play()
            }
        } else {
            // Update currentAudio if already playing
            currentAudio = audios.find { it.uri.toString() == audioUri }
            viewModel.setActiveAudio(audioUri)
        }
        
        try {
            while (true) {
                if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                    currentPosition = player.currentPosition
                }
                delay(1000)
            }
        } catch (e: Exception) {
            // Player might have been released
        } finally {
            player.removeListener(listener)
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutes = if (sleepTimerActive) (sleepTimerTimeLeft / 60000).toInt() else 0,
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            }
        )
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                val audio = currentAudio
                if (audio != null) {
                    viewModel.addToPlaylist(playlistId, audio.uri.toString(), "audio")
                    Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
                showPlaylistDialog = false
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePlaybackProgress()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Blurred Background Image
        AsyncImage(
            model = albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 50.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )
        
        // Dynamic Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBgColor.copy(alpha = 0.7f),
                            Color(0xFF1A1A1A)
                        )
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMoreOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMoreOptions,
                            onDismissRequest = { showMoreOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = { 
                                    showMoreOptions = false
                                    showPlaylistDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (sleepTimerActive) "Sleep Timer: ${formatTime(sleepTimerTimeLeft)}" else "Sleep Timer") },
                                onClick = { 
                                    showMoreOptions = false
                                    showSleepTimerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = if (sleepTimerActive) Color(0xFFFF6600) else Color.White) }
                            )
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                onClick = { 
                                    showMoreOptions = false
                                    try {
                                        val eqIntent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                                        eqIntent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                        eqIntent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                        context.startActivity(eqIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No equalizer app found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (controller == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                val player = controller!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = albumArtUri,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.MusicNote),
                            fallback = rememberVectorPainter(Icons.Default.MusicNote)
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Audio Info
                    Text(
                        text = currentAudio?.name ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                    Text(
                        text = currentAudio?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Seek Bar
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            currentPosition = it.toLong()
                            player.seekTo(it.toLong())
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isShuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
                        IconButton(onClick = {
                            isShuffle = !isShuffle
                            player.shuffleModeEnabled = isShuffle
                        }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) Color(0xFFFF6600) else Color.White.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(onClick = {
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            player.repeatMode = nextMode
                        }) {
                            Icon(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFFFF6600) else Color.White.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }
                        
                        FilledIconButton(
                            onClick = { if (isPlaying) player.pause() else player.play() },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF6600)
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(onClick = { player.seekToNextMediaItem() }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }

                        // Placeholder to keep spacing even
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
