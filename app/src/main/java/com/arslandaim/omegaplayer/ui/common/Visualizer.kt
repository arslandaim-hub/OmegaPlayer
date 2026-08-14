package com.arslandaim.omegaplayer.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF6600)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.1f,
        animationSpec = tween(500),
        label = "amplitude"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val barCount = 40
        val barWidth = width / barCount

        for (i in 0 until barCount) {
            val x = i * barWidth + barWidth / 2
            // Calculate a wave-like height
            val variation = sin(phase + i * 0.5f) * 0.4f + 0.6f
            val barHeight = (height * 0.8f * variation * amplitudeMultiplier).coerceAtLeast(4.dp.toPx())
            
            drawLine(
                color = color.copy(alpha = 0.8f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}
