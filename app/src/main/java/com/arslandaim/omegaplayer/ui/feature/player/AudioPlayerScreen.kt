/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.ui.common.WaveformVisualizer
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.arslandaim.omegaplayer.data.AudioModel
import android.content.Context
import android.app.ActivityManager
import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    stopAfterCurrent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onStopAfterCurrentToggle: (Boolean) -> Unit
) {
    var minutes by remember { mutableIntStateOf(if (currentMinutes > 0) currentMinutes else 30) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!stopAfterCurrent) {
                    Text("${minutes} minutes")
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = it.toInt() },
                        valueRange = 0f..120f,
                        steps = 23 // 5 min increments if 0-120
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = stopAfterCurrent,
                        onCheckedChange = { onStopAfterCurrentToggle(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop after current track")
                }

                if (currentMinutes > 0 || stopAfterCurrent) {
                    TextButton(onClick = { 
                        onConfirm(0)
                        onStopAfterCurrentToggle(false)
                    }) {
                        Text("Turn Off", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (!stopAfterCurrent) {
                Button(onClick = { onConfirm(minutes) }) {
                    Text("Set")
                }
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
    
    // Performance Check: Fallback for low-end devices
    val isLowEndDevice = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.isLowRamDevice || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    }

    val scope = rememberCoroutineScope()
    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val controller by viewModel.mediaController.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepTimerActive by viewModel.sleepTimerActive.collectAsStateWithLifecycle()
    val sleepTimerTimeLeft by viewModel.sleepTimerTimeLeft.collectAsStateWithLifecycle()
    val stopAfterCurrent by viewModel.stopAfterCurrent.collectAsStateWithLifecycle()
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
    var playbackSpeed by remember { mutableFloatStateOf(controller?.playbackParameters?.speed ?: 1.0f) }
    
    var dominantColor by remember { mutableStateOf(Color(0xFF1A1A1A)) }
    val animatedBgColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 2000),
        label = "bgColor"
    )

    // Breathing Animation for Album Art
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val artScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artScale"
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
            currentMinutes = if (sleepTimerActive && !stopAfterCurrent) (sleepTimerTimeLeft / 60000).toInt() else 0,
            stopAfterCurrent = stopAfterCurrent,
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onStopAfterCurrentToggle = { enabled ->
                viewModel.setStopAfterCurrent(enabled)
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
            .background(Color(0xFF0F0F0F))
    ) {
        // Animated Background Gradients
        if (!isLowEndDevice) {
            val transition = rememberInfiniteTransition(label = "mesh")
            val offset1 by transition.animateFloat(
                initialValue = 0f, targetValue = 1000f,
                animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "o1"
            )
            val offset2 by transition.animateFloat(
                initialValue = 1000f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "o2"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.6f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(animatedBgColor.copy(alpha = 0.4f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(offset1, offset2),
                            radius = 1200f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.4f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(offset2, offset1),
                            radius = 1000f
                        )
                    )
            )
        }

        // Background Image with Conditional Blur
        AsyncImage(
            model = albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLowEndDevice) Modifier else Modifier.blur(radius = 60.dp)),
            contentScale = ContentScale.Crop,
            alpha = if (isLowEndDevice) 0.15f else 0.3f
        )
        
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isLowEndDevice) Color.Black.copy(alpha = 0.7f) else animatedBgColor.copy(alpha = 0.5f),
                            Color(0xFF0F0F0F)
                        )
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                        }
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
                                text = { 
                                    val text = if (stopAfterCurrent) "Sleep: End of Track" 
                                              else if (sleepTimerActive) "Sleep Timer: ${formatTime(sleepTimerTimeLeft)}" 
                                              else "Sleep Timer"
                                    Text(text)
                                },
                                onClick = { 
                                    showMoreOptions = false
                                    showSleepTimerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else Color.White) }
                            )
                            DropdownMenuItem(
                                text = { Text("Playback Speed (${playbackSpeed}x)") },
                                onClick = { 
                                    // Cycles speeds
                                    val nextSpeed = when(playbackSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.75f
                                        else -> 1.0f
                                    }
                                    playbackSpeed = nextSpeed
                                    controller?.setPlaybackSpeed(nextSpeed)
                                    showMoreOptions = false
                                },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val player = controller!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Album Art with Breathing Animation
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .graphicsLayer {
                                scaleX = artScale
                                scaleY = artScale
                            }
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
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

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Audio Info
                        Text(
                            text = currentAudio?.name ?: "Unknown Title",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentAudio?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Waveform Visualizer
                    WaveformVisualizer(
                        isPlaying = isPlaying,
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column {
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
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                            )
                        }

                        IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                        
                        FilledIconButton(
                            onClick = { if (isPlaying) player.pause() else player.play() },
                            modifier = Modifier.size(84.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        IconButton(onClick = { player.seekToNextMediaItem() }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }

                        var isShuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
                        IconButton(onClick = {
                            isShuffle = !isShuffle
                            player.shuffleModeEnabled = isShuffle
                        }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.6f).padding(16.dp)) {
                Text(
                    "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn {
                    items(audios) { audio ->
                        val isCurrent = currentAudio?.id == audio.id
                        ListItem(
                            headlineContent = { 
                                Text(
                                    audio.name, 
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White
                                ) 
                            },
                            supportingContent = { Text(audio.artist, color = Color.Gray) },
                            leadingContent = {
                                AsyncImage(
                                    model = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), audio.albumId),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = rememberVectorPainter(Icons.Default.MusicNote)
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.togglePlayPause(audio)
                                showQueueSheet = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
