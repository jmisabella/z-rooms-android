package com.jmisabella.zrooms

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BreathingBackground(color: Color, modifier: Modifier = Modifier) {
    val hsva = FloatArray(3)
    android.graphics.Color.colorToHSV(color.value.toInt(), hsva)  // hue, sat, value
    val baseHue = hsva[0] / 360f
    val baseSaturation = hsva[1]
    val baseBrightness = hsva[2]

    val transition = rememberInfiniteTransition(label = "breathing")

    Box(modifier) {
        Box(Modifier.fillMaxSize().background(color))

        (0 until 15).forEach { i ->
            BlobView(i, transition, baseHue, baseSaturation, baseBrightness)
        }
    }
}