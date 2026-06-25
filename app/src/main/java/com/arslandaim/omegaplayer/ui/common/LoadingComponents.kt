package com.arslandaim.omegaplayer.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ModernLoadingDialog(
    title: String = "Processing...",
    onDismissRequest: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ModernProgressIndicator()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please wait a moment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModernProgressIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF87CEEB).copy(alpha = 0.1f),
                            Color(0xFFFF6600).copy(alpha = 0.1f),
                            Color(0xFF87CEEB).copy(alpha = 0.1f)
                        )
                    )
                )
                .rotate(angle)
        )
        
        // Rotating gradient border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .rotate(angle)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF87CEEB), // Light Blue
                            Color(0xFFFF6600), // Orange
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Inner circle
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Ω",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF87CEEB),
                                Color(0xFFFF6600)
                            )
                        )
                    )
                )
            }
        }
    }
}
