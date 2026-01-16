package com.jmisabella.zrooms

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Three pulsing dots loading indicator for story→poetry transition
 */
@Composable
fun LoadingDotsIndicator(
    color: Color = Color(0xFFFFB74D),  // Orange/amber
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val delays = listOf(0, 150, 300)  // Stagger the dots

    Row(modifier = modifier) {
        delays.forEach { delay ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$delay"
            )

            Canvas(
                modifier = Modifier
                    .size(6.dp)
                    .padding(horizontal = 2.dp)
            ) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2
                )
            }
        }
    }
}
