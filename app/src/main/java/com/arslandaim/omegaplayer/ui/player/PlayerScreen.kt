/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

import com.arslandaim.omegaplayer.viewmodel.VideoViewModel

@OptIn(UnstableApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    videoUri: String, 
    viewModel: VideoViewModel,
    isDarkTheme: Boolean, 
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val activity = context as? Activity
    val hazeState = remember { HazeState() }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
    
    // Scoped State from ViewModel
    val isBackgroundPlayEnabled by viewModel.isBackgroundPlayEnabled.collectAsStateWithLifecycle()
    

    // Smooth transition state
    var isTransitionComplete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        isTransitionComplete = true
    }

    val exoPlayer = remember { viewModel.getPlayer(context) }

    // MX Player States (Local to this session)
    var volume by remember { mutableFloatStateOf(0.5f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Back handler to handle custom navigation and lock state
    BackHandler(enabled = true) {
        if (isBackgroundPlayEnabled && isLocked) {
            Toast.makeText(context, "Unlock the player to exit", Toast.LENGTH_SHORT).show()
        } else {
            onBack()
        }
    }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLandscape by rememberSaveable { mutableStateOf(false) }
    var aspectRatio by remember { mutableIntStateOf(0) } // 0: Fit, 1: Zoom, 2: Stretch
    var isHardwareAccelerated by rememberSaveable { mutableStateOf(true) }
    
    val currentVideo = remember(videoUri, viewModel.videos.collectAsState().value) {
        viewModel.getCurrentVideo()
    }
    
    var showInfoDialog by remember { mutableStateOf(false) }
    
    val lifecycleOwner = LocalLifecycleOwner.current

    // Use rememberUpdatedState for stable reference in observers/disposables
    val currentBackgroundPlay = rememberUpdatedState(isBackgroundPlayEnabled)

    LaunchedEffect(videoUri) {
        val newUri = Uri.parse(videoUri)
        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri
        if (currentUri != newUri) {
            playbackError = null
            viewModel.setActiveVideo(videoUri)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(MediaItem.fromUri(newUri))
            // Exact seek can be slow on huge files; use logic from ViewModel's CLOSEST_SYNC 
            // but ensure we start at the beginning for new videos
            exoPlayer.prepare()
        }
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val errorType = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network error"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Decoder error"
                    else -> "Unexpected error"
                }
                playbackError = "$errorType: ${error.message}"
                Log.e("PlayerScreen", "ExoPlayer Error ($errorType): ${error.message}", error)
                
                // Auto-retry once for common transient errors
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playbackError = null
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Handles Backgrounding (Home Button)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val isInteractive = powerManager.isInteractive
                // If minimized while locked OR if background play is simply disabled
                if (isLocked || !currentBackgroundPlay.value) {
                    if (isLocked) {
                        if (isInteractive) {
                            // User minimized app while locked -> STOP
                            viewModel.toggleBackgroundPlay(context, false)
                            exoPlayer.pause()
                        } else {
                            // Screen turned off while locked -> CONTINUE
                            // We do nothing, allowing the service to take over
                        }
                    } else {
                        // Not locked, background play disabled -> PAUSE
                        exoPlayer.pause()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handles Screen Disposal (Back Button)
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = !isDarkTheme
            controller?.isAppearanceLightNavigationBars = !isDarkTheme
            
            // Restore system brightness (set to -1f to return to system default)
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = -1f
            activity?.window?.attributes = layoutParams

            
            if (!currentBackgroundPlay.value) {
                exoPlayer.pause()
                viewModel.setActiveVideo(null)
            }
        }
    }
    
    // UI HUD States
    var showSeekForwardAnimation by remember { mutableStateOf(false) }
    var showSeekBackwardAnimation by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (!isLocked) {
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            panOffset += offsetChange
        }
    }

    var isVolumeVisible by remember { mutableStateOf(false) }
    var isBrightnessVisible by remember { mutableStateOf(false) }
    var isSeekHUDVisible by remember { mutableStateOf(false) }
    var seekOffsetHUD by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    var showPlayPausePulse by remember { mutableStateOf<Boolean?>(null) } // null: none, true: play, false: pause
    var pulseTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isControlsVisible, isLocked) {
        if (isControlsVisible && !isLocked) {
            delay(5000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            delay(500.milliseconds)
        }
    }

    val videoName = videoUri.substringAfterLast("/").substringBeforeLast(".")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isTransitionComplete) {
            with(sharedTransitionScope) {
                AndroidView(
                    factory = { ctx ->
                        val view = android.view.LayoutInflater.from(ctx).inflate(com.arslandaim.omegaplayer.R.layout.player_view, null) as PlayerView
                        view.apply {
                            player = exoPlayer
                            // TextureView is already set via XML for smooth transitions
                        }
                    },
                    update = { playerView ->
                        playerView.resizeMode = when (aspectRatio) {
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onRelease = { playerView ->
                        if (!currentBackgroundPlay.value) {
                            playerView.player = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedBounds(
                            rememberSharedContentState(key = "video_bounds_$videoUri"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                        )
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                        .transformable(state = transformState)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray.copy(alpha = 0.5f)))
        }

        // Gesture Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(onTap = { isControlsVisible = true })
                    } else {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible },
                            onDoubleTap = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (offset.x < size.width / 2) {
                                    exoPlayer.seekBack()
                                    showSeekBackwardAnimation = true
                                } else {
                                    exoPlayer.seekForward()
                                    showSeekForwardAnimation = true
                                }
                            },
                            onLongPress = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    showPlayPausePulse = false
                                } else {
                                    exoPlayer.play()
                                    showPlayPausePulse = true
                                }
                                pulseTrigger++
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures(
                            onDragStart = { 
                                isSeekHUDVisible = false
                                seekOffsetHUD = 0 
                            },
                            onDragEnd = {
                                if (isSeekHUDVisible) {
                                    exoPlayer.seekTo(exoPlayer.currentPosition + seekOffsetHUD)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                isSeekHUDVisible = false
                                isVolumeVisible = false
                                isBrightnessVisible = false
                            },
                            onDrag = { change, dragAmount ->
                                val width = size.width
                                val height = size.height
                                if (abs(dragAmount.x) > abs(dragAmount.y) && !isVolumeVisible && !isBrightnessVisible) {
                                    isSeekHUDVisible = true
                                    val oldOffset = seekOffsetHUD
                                    seekOffsetHUD += (dragAmount.x * 100).toLong()
                                    if (abs(seekOffsetHUD / 5000) != abs(oldOffset / 5000)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else if (!isSeekHUDVisible) {
                                    if (change.position.x < width / 2) {
                                        val oldBrightness = brightness
                                        brightness = (brightness - dragAmount.y / height).coerceIn(0f, 1f)
                                        if (abs(brightness - oldBrightness) > 0.05f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        setBrightness(context, brightness)
                                        isBrightnessVisible = true
                                    } else {
                                        val oldVolume = volume
                                        volume = (volume - dragAmount.y / height).coerceIn(0f, 1f)
                                        if (abs(volume - oldVolume) > 0.05f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        exoPlayer.volume = volume
                                        isVolumeVisible = true
                                    }
                                }
                            }
                        )
                    }
                }
        )

        // Lock Icon HUD
        if (isControlsVisible || isLocked) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(
                    onClick = { 
                        if (isLocked) { isLocked = false; isControlsVisible = true } 
                        else { isLocked = true; isControlsVisible = false } 
                    },
                    modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock",
                        tint = if (isLocked) Color.Red else Color.White
                    )
                }
            }
        }

        SeekHUD(isVisible = isSeekHUDVisible, offset = seekOffsetHUD, currentPosition = currentPosition)

        SeekAnimationOverlay(
            isVisible = showSeekBackwardAnimation,
            isForward = false,
            onAnimationFinished = { showSeekBackwardAnimation = false }
        )
        SeekAnimationOverlay(
            isVisible = showSeekForwardAnimation,
            isForward = true,
            onAnimationFinished = { showSeekForwardAnimation = false }
        )

        VerticalIndicator(
            value = volume,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            visible = isVolumeVisible && !isLocked,
            color = Color(0xFF81C784),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        
        VerticalIndicator(
            value = brightness,
            icon = Icons.Default.BrightnessMedium,
            visible = isBrightnessVisible && !isLocked,
            color = Color(0xFFFFD54F),
            modifier = Modifier.align(Alignment.CenterStart)
        )

        PlayPausePulse(
            isPlay = showPlayPausePulse ?: true,
            trigger = pulseTrigger,
            visible = showPlayPausePulse != null
        )

        AnimatedVisibility(
            visible = isControlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
                    .haze(hazeState)
            ) {
                PlayerControls(
                    exoPlayer = exoPlayer,
                    videoName = videoName,
                    currentPosition = currentPosition,
                    duration = duration,
                    aspectRatio = aspectRatio,
                    isHardwareAccelerated = isHardwareAccelerated,
                    isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                    onBack = onBack,
                    onSpeedChange = { speed ->
                        playbackSpeed = speed
                        exoPlayer.setPlaybackSpeed(speed)
                    },
                    onRotationChange = {
                        isLandscape = !isLandscape
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    onAspectRatioToggle = {
                        aspectRatio = (aspectRatio + 1) % 3
                    },
                    onHardwareToggle = {
                        isHardwareAccelerated = !isHardwareAccelerated
                        val mode = if (isHardwareAccelerated) "Hardware" else "Software"
                        Toast.makeText(context, "$mode Decoding Active", Toast.LENGTH_SHORT).show()
                    },
                    onBackgroundPlayToggle = {
                        viewModel.toggleBackgroundPlay(context, !isBackgroundPlayEnabled)
                        if (!isBackgroundPlayEnabled) { // It was just enabled
                            isLocked = true
                            isControlsVisible = false
                        }
                    },
                    onInfoClick = { showInfoDialog = true }
                )
            }
        }

        // Background Audio Lock Overlay
        if (isBackgroundPlayEnabled && isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { 
                            Toast.makeText(context, "Unlock to use controls", Toast.LENGTH_SHORT).show()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Headset,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Playing Audio in Background",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Turn screen OFF to save battery",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { 
                            isLocked = false
                            isControlsVisible = true
                            viewModel.toggleBackgroundPlay(context, false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Player")
                    }
                }
            }
        }

        if (showInfoDialog && currentVideo != null) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Video Information", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Name", currentVideo.name)
                        InfoRow("Size", "${currentVideo.size / (1024 * 1024)} MB")
                        InfoRow("Path", currentVideo.path)
                        InfoRow("Duration", formatDuration(currentVideo.duration))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (playbackError != null) {
            PlaybackErrorOverlay(
                error = playbackError!!,
                onRetry = {
                    playbackError = null
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            )
        }
    }
}

@Composable
fun PlaybackErrorOverlay(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Playback Error",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                error,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Playback")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

@Composable
fun PlayerControls(
    exoPlayer: Player,
    videoName: String,
    currentPosition: Long,
    duration: Long,
    aspectRatio: Int,
    isHardwareAccelerated: Boolean,
    isBackgroundPlayEnabled: Boolean,
    onBack: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onRotationChange: () -> Unit,
    onAspectRatioToggle: () -> Unit,
    onHardwareToggle: () -> Unit,
    onBackgroundPlayToggle: () -> Unit,
    onInfoClick: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 28.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = videoName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onBackgroundPlayToggle) {
                Icon(
                    imageVector = Icons.Default.Headset, 
                    contentDescription = "Background Play", 
                    tint = if (isBackgroundPlayEnabled) Color(0xFF4CAF50) else Color.White
                )
            }
            IconButton(onClick = onHardwareToggle) {
                Text(
                    text = if (isHardwareAccelerated) "HW" else "SW",
                    color = if (isHardwareAccelerated) Color(0xFF4CAF50) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Center Controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            IconButton(onClick = { exoPlayer.seekBack() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(
                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play", tint = Color.White, modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = { exoPlayer.seekForward() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        // Bottom Bar
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { exoPlayer.seekTo((it * duration).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFFF6600),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(formatTime(duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                IconButton(onClick = onRotationChange) { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) }
                IconButton(onClick = onAspectRatioToggle) {
                    Icon(when(aspectRatio) { 1 -> Icons.Default.Fullscreen; 2 -> Icons.Default.AspectRatio; else -> Icons.Default.FitScreen }, null, tint = Color.White)
                }
                TextButton(onClick = { onSpeedChange(if (exoPlayer.playbackParameters.speed >= 2f) 1f else exoPlayer.playbackParameters.speed + 0.5f) }) {
                    Text("${exoPlayer.playbackParameters.speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SeekHUD(isVisible: Boolean, offset: Long, currentPosition: Long) {
    AnimatedVisibility(
        visible = isVisible, 
        enter = fadeIn() + scaleIn(), 
        exit = fadeOut() + scaleOut()
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f), 
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val targetTime = (currentPosition + offset).coerceAtLeast(0)
                    Text(
                        text = formatTime(targetTime),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = (if (offset >= 0) "+" else "") + "${offset / 1000}s",
                        color = if (offset >= 0) Color(0xFF81C784) else Color(0xFFE57373),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SeekAnimationOverlay(
    isVisible: Boolean,
    isForward: Boolean,
    onAnimationFinished: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 1.2f),
        modifier = Modifier.fillMaxSize()
    ) {
        LaunchedEffect(isVisible) {
            if (isVisible) {
                delay(600)
                onAnimationFinished()
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(100.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "seek_arrows")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Icon(
                        imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp).graphicsLayer(alpha = alpha)
                    )
                    Text(
                        text = if (isForward) "+10s" else "-10s",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalIndicator(
    value: Float, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    visible: Boolean, 
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(targetValue = value)
    AnimatedVisibility(
        visible = visible, 
        enter = fadeIn() + slideInHorizontally { if (modifier.toString().contains("CenterStart")) -20 else 20 }, 
        exit = fadeOut() + slideOutHorizontally { if (modifier.toString().contains("CenterStart")) -20 else 20 }, 
        modifier = modifier.fillMaxHeight().padding(vertical = 180.dp, horizontal = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier
                .width(40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp)), 
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedValue)
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun PlayPausePulse(isPlay: Boolean, trigger: Int, visible: Boolean) {
    var isAnimVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            isAnimVisible = true
            delay(500)
            isAnimVisible = false
        }
    }

    AnimatedVisibility(
        visible = isAnimVisible,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 1.5f) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun setBrightness(context: Context, brightness: Float) {
    val activity = context as? Activity ?: return
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness
    activity.window.attributes = layoutParams
}
