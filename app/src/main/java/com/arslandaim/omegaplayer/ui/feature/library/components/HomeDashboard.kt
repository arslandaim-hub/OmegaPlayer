package com.arslandaim.omegaplayer.ui.feature.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.ui.feature.library.MediaTab
import com.arslandaim.omegaplayer.viewmodel.StorageStats
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun HomeDashboard(
    selectedTab: MediaTab,
    storageStats: StorageStats,
    onTabSelected: (MediaTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Modern Pill Tab Row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val background by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "tabBg"
                    )
                    val contentColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabContent"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab) },
                        shape = RoundedCornerShape(24.dp),
                        color = background
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }

        if (selectedTab == MediaTab.VIDEOS) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearStorageVisualization(stats = storageStats)
        }
    }
}

@Composable
fun LinearStorageVisualization(stats: StorageStats, modifier: Modifier = Modifier) {
    val videoColor = MaterialTheme.colorScheme.primary
    val audioColor = MaterialTheme.colorScheme.secondary
    val otherColor = MaterialTheme.colorScheme.outline
    val freeColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val total = stats.totalBytes.toFloat()
    if (total <= 0f) return

    val videoWeight = (stats.videoBytes / total)
    val audioWeight = (stats.audioBytes / total)
    val otherWeight = (stats.otherBytes / total)
    val freeWeight = (stats.freeBytes / total)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stats.formatSize(stats.totalBytes - stats.freeBytes)} / ${stats.formatSize(stats.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Linear Storage Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(freeColor)
            ) {
                if (videoWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(videoWeight).background(videoColor))
                if (audioWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(audioWeight).background(audioColor))
                if (otherWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(otherWeight).background(otherColor))
                if (freeWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(freeWeight).background(Color.Transparent))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageLegendItemSmall("Videos", videoColor)
                StorageLegendItemSmall("Audios", audioColor)
                StorageLegendItemSmall("Other", otherColor)
                StorageLegendItemSmall("Free", Color(0xFFE4E4E7))
            }
        }
    }
}

@Composable
fun StorageLegendItemSmall(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecentPlaybackSection(
    recentPlayback: List<RecentPlayback>,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    onViewAllClick: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onViewAllClick) {
                Text("View All", color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(recentPlayback, key = { it.uri }) { item ->
                RecentPlaybackItem(
                    item = item,
                    onClick = {
                        val encodedUri = URLEncoder.encode(item.uri, StandardCharsets.UTF_8.toString())
                        if (item.mediaType == "video") onVideoClick(encodedUri)
                        else onAudioClick(encodedUri)
                    }
                )
            }
        }
    }
}

@Composable
fun ModernOmegaIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.primary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ω",
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModernOmegaIconPreview() {
    MaterialTheme { Box(modifier = Modifier.padding(16.dp)) { ModernOmegaIcon() } }
}
