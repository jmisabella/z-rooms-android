package com.jmisabella.zrooms

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun BreathingBackground(color: Color, modifier: Modifier = Modifier) {
    val hsva = FloatArray(3)
    android.graphics.Color.colorToHSV(color.value.toInt(), hsva)
    val baseHue = hsva[0] / 360f
    val baseSaturation = hsva[1]
    val baseBrightness = hsva[2]

    val transition = rememberInfiniteTransition(label = "breathing")

    // Animation for pulsing
    val scale1 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val scale2 by transition.animateFloat(
        initialValue = 1.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )
    val opacity1 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacity1"
    )
    val opacity2 by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacity2"
    )
    // Animation for overall fade to darkness over 3 minutes
    val fadeOpacity by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(180000, easing = LinearEasing), // 3 minutes
            repeatMode = RepeatMode.Restart
        ),
        label = "fadeOpacity"
    )

    // Animation for gentle movement
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Derive complementary colors for 2-3 color palette
    val color1 = hsvToColor(baseHue, baseSaturation * 0.9f, baseBrightness * 1.1f.coerceAtMost(1f))
    val color2 = hsvToColor((baseHue + 0.12f).mod(1f), baseSaturation * 1.0f.coerceAtMost(1f), baseBrightness * 0.95f)
    val color3 = hsvToColor((baseHue - 0.12f + 1f).mod(1f), baseSaturation * 0.85f, baseBrightness * 1.15f.coerceAtMost(1f))

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val maxDim = max(width, height)

        // Calculate moving center for dynamic effect
        val orbitRadiusX = width * 0.1f
        val orbitRadiusY = height * 0.1f
        val centerX = width / 2 + (orbitRadiusX * cos(Math.toRadians(angle.toDouble()).toFloat()))
        val centerY = height / 2 + (orbitRadiusY * sin(Math.toRadians(angle.toDouble()).toFloat()))

        val movingCenter = Offset(centerX, centerY)

        Box(Modifier.fillMaxSize().background(color)) {
            // Layer 1: Pulsing gradient with moving center (brighter in center)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = opacity1 * fadeOpacity), Color.Transparent),
                            center = movingCenter,
                            radius = (maxDim / 1.5f) * scale1
                        )
                    )
            )
            // Layer 2: Counter-pulsing gradient with fixed center for depth
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                color2.copy(alpha = opacity2 * fadeOpacity),
                                color3.copy(alpha = opacity2 * 0.7f * fadeOpacity),
                                Color.Transparent
                            ),
                            center = Offset(width / 2, height / 2),
                            radius = (maxDim / 2f) * scale2
                        )
                    )
            )
            // Layer 3: Subtle blob layer for additional glowing effect
            for (i in 0 until 8) { // Reduced number for performance
                BlobView(
                    i = i,
                    transition = transition,
                    baseHue = baseHue,
                    baseSaturation = baseSaturation,
                    baseBrightness = baseBrightness,
                    fadeOpacity = fadeOpacity
                )
            }
        }
    }
}