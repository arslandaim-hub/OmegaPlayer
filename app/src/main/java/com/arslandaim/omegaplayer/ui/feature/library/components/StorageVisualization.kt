/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslandaim.omegaplayer.viewmodel.StorageStats
import java.util.Locale

@Composable
fun StorageVisualization(
    stats: StorageStats,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(stats) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    val videoColor = Color(0xFFFF6600) // Orange
    val audioColor = Color(0xFF87CEEB) // Light Blue
    val otherColor = Color(0xFF71717A) // Zinc/Grey
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val innerRadius = (size.minDimension - strokeWidth) / 2
                
                // Track
                drawCircle(
                    color = trackColor,
                    radius = innerRadius,
                    style = Stroke(width = strokeWidth)
                )

                val usedBytes = stats.totalBytes - stats.freeBytes
                if (usedBytes > 0) {
                    val videoAngle = (stats.videoBytes.toFloat() / stats.totalBytes) * 360f * animationProgress.value
                    val audioAngle = (stats.audioBytes.toFloat() / stats.totalBytes) * 360f * animationProgress.value
                    val otherAngle = (stats.otherBytes.toFloat() / stats.totalBytes) * 360f * animationProgress.value

                    var startAngle = -90f

                    // Other
                    drawArc(
                        color = otherColor,
                        startAngle = startAngle,
                        sweepAngle = otherAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += otherAngle

                    // Audio
                    drawArc(
                        color = audioColor,
                        startAngle = startAngle,
                        sweepAngle = audioAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += audioAngle

                    // Video
                    drawArc(
                        color = videoColor,
                        startAngle = startAngle,
                        sweepAngle = videoAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val percent = if (stats.totalBytes > 0) {
                    ((stats.totalBytes - stats.freeBytes).toFloat() / stats.totalBytes * 100).toInt()
                } else 0
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Used",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${formatSize(stats.totalBytes - stats.freeBytes)} of ${formatSize(stats.totalBytes)} used",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendItem(color = videoColor, label = "Videos: ${formatSize(stats.videoBytes)}")
                Spacer(modifier = Modifier.width(12.dp))
                LegendItem(color = audioColor, label = "Audios: ${formatSize(stats.audioBytes)}")
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
