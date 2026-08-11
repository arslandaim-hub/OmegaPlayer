package com.arslandaim.omegaplayer.ui.common

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.arslandaim.omegaplayer.media.PlaybackConnection
import kotlinx.coroutines.delay

@Composable
fun NowPlayingBar(
    playbackConnection: PlaybackConnection,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaItem by playbackConnection.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playbackConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaController by playbackConnection.mediaController.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Adaptive Color State
    var dominantColor by remember { mutableStateOf(Color.Transparent) }
    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "adaptiveTint"
    )

    // Playback Position for Progress Bar
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, currentMediaItem) {
        val player = mediaController ?: return@LaunchedEffect
        while (isPlaying) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Extract palette from artwork
    LaunchedEffect(currentMediaItem) {
        val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
        if (artworkUri != null) {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .build()
            
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(0x00000000)
                        )
                    )
                    dominantColor = Color(color).copy(alpha = 0.15f)
                }
            }
        } else {
            dominantColor = Color.Transparent
        }
    }

    // Artwork Pulse Animation
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artworkPulse"
    )

    AnimatedVisibility(
        visible = currentMediaItem != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val metadata = currentMediaItem?.mediaMetadata
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp) // Slightly taller for premium feel
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onBarClick() }
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                        },
                        onDragEnd = {
                            if (totalDragY < -100f) {
                                // Swipe Up -> Open Player
                                onBarClick()
                            } else if (totalDragX > 150f) {
                                // Swipe Right -> Previous
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mediaController?.seekToPrevious()
                            } else if (totalDragX < -150f) {
                                // Swipe Left -> Next
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mediaController?.seekToNext()
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragCancel = {
                            totalDragX = 0f
                            totalDragY = 0f
                        }
                    )
                },
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.8f),
            tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Adaptive Tint Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(animatedDominantColor, Color.Transparent)
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated Artwork
                    AsyncImage(
                        model = metadata?.artworkUri ?: metadata?.artworkData,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .scale(if (isPlaying) artworkScale else 1.0f)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(Icons.Default.MusicNote),
                        fallback = rememberVectorPainter(Icons.Default.MusicNote)
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = metadata?.title?.toString() ?: "Unknown",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.2.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = metadata?.artist?.toString() ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cut Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mediaController?.stop()
                            mediaController?.clearMediaItems()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cut",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Play/Pause with Haptic
                    IconButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isPlaying) mediaController?.pause() else mediaController?.play()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Mini Progress Bar
                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { position.toFloat() / duration.toFloat() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        trackColor = Color.Transparent,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}
