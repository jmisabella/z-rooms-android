package com.jmisabella.zrooms

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BlobView(
    i: Int,
    transition: InfiniteTransition,
    baseHue: Float,
    baseSaturation: Float,
    baseBrightness: Float,
    fadeOpacity: Float
) {
    val numBlobs = 8
    val blobSize: Dp = 250.dp
    val blurRadius: Dp = 80.dp
    val amplitude = 300f
    val speed = 0.6f
    val opacity = 0.5f
    val hueVariation = 0.12f
    val satVariation = 0.4f
    val brightVariation = 0.25f
    val brightBias = 0.2f // Positive bias for brighter center

    // Assign blobs to one of 3 color groups for 2-3 color palette
    val hueShifts = listOf(0f, 0.12f, -0.12f)
    val baseHueShift = hueShifts[i % 3]

    val phase = i.toFloat() * Math.PI.toFloat() * 2 / numBlobs

    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val x by remember(t) { derivedStateOf { sin((t * speed + phase).toDouble()).toFloat() * amplitude } }
    val y by remember(t) { derivedStateOf { cos((t * speed + phase * 1.2f).toDouble()).toFloat() * amplitude } }
    val hueOffset by remember(t) { derivedStateOf { sin((t * 0.08f + phase).toDouble()).toFloat() * hueVariation } }
    val satOffset by remember(t) { derivedStateOf { cos((t * 0.12f + phase * 2).toDouble()).toFloat() * satVariation } }
    val brightOffset by remember(t) {
        derivedStateOf { sin((t * 0.15f + phase * 3).toDouble()).toFloat() * brightVariation + brightBias }
    }

    val variantColor = hsvToColor(
        hue = (baseHue + baseHueShift + hueOffset).mod(1f),
        sat = (baseSaturation + satOffset).coerceIn(0.3f, 1f),
        value = (baseBrightness + brightOffset).coerceIn(0.5f, 1f) // Higher min value for brighter effect
    )

    Box(
        modifier = Modifier
            .width(blobSize)
            .height(blobSize)
            .offset(x = x.dp, y = y.dp)
            .background(variantColor.copy(alpha = opacity * fadeOpacity), shape = CircleShape)
            .blur(radius = blurRadius)
    )
}