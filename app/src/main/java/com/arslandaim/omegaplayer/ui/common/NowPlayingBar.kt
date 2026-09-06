/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.common

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.arslandaim.omegaplayer.media.PlaybackConnection

@Composable
fun NowPlayingBar(
    playbackConnection: PlaybackConnection,
    onAudioClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaItem by playbackConnection.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playbackConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaController by playbackConnection.mediaController.collectAsStateWithLifecycle()

    val isVideo = remember(currentMediaItem) {
        isVideoMediaItem(currentMediaItem)
    }

    AnimatedVisibility(
        visible = currentMediaItem != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val item = currentMediaItem ?: return@AnimatedVisibility
        val uriString = item.localConfiguration?.uri?.toString() ?: ""

        if (isVideo) {
            VideoMiniPlayer(
                mediaItem = item,
                isPlaying = isPlaying,
                mediaController = mediaController,
                onExpandClick = {
                    if (uriString.isNotEmpty()) {
                        onVideoClick(uriString)
                    }
                },
                onCloseClick = {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                }
            )
        } else {
            AudioMiniPlayer(
                mediaItem = item,
                isPlaying = isPlaying,
                mediaController = mediaController,
                onBarClick = {
                    if (uriString.isNotEmpty()) {
                        onAudioClick(uriString)
                    }
                },
                onCloseClick = {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                }
            )
        }
    }
}

private fun isVideoMediaItem(mediaItem: MediaItem?): Boolean {
    if (mediaItem == null) return false
    val uriStr = mediaItem.localConfiguration?.uri?.toString()?.lowercase() ?: ""
    val mimeType = mediaItem.localConfiguration?.mimeType?.lowercase() ?: ""
    if (mimeType.startsWith("video/")) return true
    val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".m4v", ".flv", ".ts")
    return videoExtensions.any { uriStr.contains(it) }
}
