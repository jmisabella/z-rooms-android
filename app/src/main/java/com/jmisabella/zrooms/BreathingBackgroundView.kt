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
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacity1"
    )
    val opacity2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacity2"
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

    // Derive complementary colors
    val color1 = hsvToColor(baseHue, baseSaturation * 0.9f, baseBrightness * 1.05f.coerceAtMost(1f))
    val color2 = hsvToColor((baseHue + 0.15f) % 1f, baseSaturation * 1.1f.coerceAtMost(1f), baseBrightness * 0.95f)
    val color3 = hsvToColor((baseHue - 0.15f + 1f) % 1f, baseSaturation * 0.85f, baseBrightness * 1.1f.coerceAtMost(1f))

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val maxDim = max(width, height)

        // Calculate moving center
        val orbitRadiusX = width * 0.15f
        val orbitRadiusY = height * 0.15f
        val centerX = width / 2 + (orbitRadiusX * cos(Math.toRadians(angle.toDouble()).toFloat()))
        val centerY = height / 2 + (orbitRadiusY * sin(Math.toRadians(angle.toDouble()).toFloat()))

        val movingCenter = Offset(centerX, centerY)

        Box(Modifier.fillMaxSize().background(color)) {
            // Layer 1: Pulsing gradient with moving center
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = opacity1), Color.Transparent),
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
                            colors = listOf(color2.copy(alpha = opacity2), color3.copy(alpha = opacity2 * 0.7f), Color.Transparent),
                            center = Offset(width / 2, height / 2),
                            radius = (maxDim / 2f) * scale2
                        )
                    )
            )
        }
    }
}

//
//package com.jmisabella.zrooms
//
//import android.graphics.BlendMode
//import androidx.compose.animation.core.LinearEasing
//import androidx.compose.animation.core.RepeatMode
//import androidx.compose.animation.core.infiniteRepeatable
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.style.TextForegroundStyle.Unspecified.alpha
//import androidx.compose.ui.unit.dp
//import kotlinx.coroutines.isActive
//import kotlin.coroutines.coroutineContext
//
//@Composable
//fun BreathingBackground(color: Color, modifier: Modifier = Modifier) {
//    val hsva = FloatArray(3)
//    android.graphics.Color.colorToHSV(color.value.toInt(), hsva)
//    val baseHue = hsva[0] / 360f
//    val baseSaturation = hsva[1]
//    val baseBrightness = hsva[2]
//
//    val startTime = remember { System.currentTimeMillis() }
//    var t by remember { mutableFloatStateOf(0f) }
//
//    LaunchedEffect(Unit) {
//        while (coroutineContext.isActive) {
//            androidx.compose.runtime.withFrameMillis { frameTime ->
//                t = ((frameTime - startTime) / 1000f)
//            }
//        }
//    }
//
//    Box(modifier.fillMaxSize().background(color)) {
//        for (i in 0 until 15) {
//            BlobView(
//                i = i,
//                t = t,
//                baseHue = baseHue,
//                baseSaturation = baseSaturation,
//                baseBrightness = baseBrightness,
//                modifier = Modifier.align(Alignment.Center)
//            )
//        }
//    }
//}
//
//@Composable
//fun BlobView(
//    i: Int,
//    t: Float,
//    baseHue: Float,
//    baseSaturation: Float,
//    baseBrightness: Float,
//    modifier: Modifier = Modifier
//) {
//    val numBlobs = 15
//    val blobSize: Dp = 250.dp
//    val blurRadius: Dp = 80.dp
//    val amplitude = 300f  // Adjusted for better spread
//    val speed = 0.75f
//    val opacity = 0.6f
//    val hueVariation = 0.1f
//    val satVariation = 0.5f
//    val brightVariation = 0.15f
//    val brightBias = 0f  // Adjusted to 0f to reduce darkness
//
//    val phase = i.toFloat() * Math.PI.toFloat() * 2 / numBlobs
//
//    val x = sin((t * speed + phase).toDouble()).toFloat() * amplitude
//    val y = cos((t * speed + phase * 1.3f).toDouble()).toFloat() * amplitude
//    val hueOffset = sin((t * 0.1f + phase).toDouble()).toFloat() * hueVariation
//    val satOffset = cos((t * 0.15f + phase * 2).toDouble()).toFloat() * satVariation
//    val brightOffset = sin((t * 0.2f + phase * 3).toDouble()).toFloat() * brightVariation + brightBias
//
//    val variantColor = hsvToColor(
//        hue = (baseHue + hueOffset).coerceIn(0f, 1f),
//        sat = (baseSaturation + satOffset).coerceIn(0f, 1f),
//        value = (baseBrightness + brightOffset).coerceIn(0.4f, 1f)  // Increased min to 0.4f for brighter effect
//    )
//
//    Box(
//        modifier = modifier
//            .width(blobSize)
//            .height(blobSize)
//            .offset(x = x.dp, y = y.dp)
//            .background(variantColor, shape = CircleShape)
//            .blur(radius = blurRadius)
//            .graphicsLayer {
//                alpha = opacity
//                this.blendMode = BlendMode.Screen  // Changed to Screen for a brighter, more appealing glow effect
//            }
//    )
//}
//
////package com.jmisabella.zrooms
////
////import androidx.compose.animation.core.InfiniteRepeatableSpec
////import androidx.compose.animation.core.LinearEasing
////import androidx.compose.animation.core.RepeatMode
////import androidx.compose.animation.core.animateFloat
////import androidx.compose.animation.core.rememberInfiniteTransition
////import androidx.compose.animation.core.tween
////import androidx.compose.foundation.background
////import androidx.compose.foundation.layout.Box
////import androidx.compose.foundation.layout.fillMaxSize
////import androidx.compose.runtime.Composable
////import androidx.compose.runtime.getValue
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.geometry.Offset
////import androidx.compose.ui.graphics.Brush
////import androidx.compose.ui.graphics.Color
////
////@Composable
////fun BreathingBackground(color: Color, modifier: Modifier = Modifier) {
////    val hsva = FloatArray(3)
////    android.graphics.Color.colorToHSV(color.value.toInt(), hsva)
////    val baseHue = hsva[0] / 360f
////    val baseSaturation = hsva[1]
////    val baseBrightness = hsva[2]
////
////    val transition = rememberInfiniteTransition(label = "breathing")
////
////    // Animation states for two layers
////    val scale1 by transition.animateFloat(
////        initialValue = 0.6f,
////        targetValue = 1.4f,
////        animationSpec = InfiniteRepeatableSpec(
////            animation = tween(6000, easing = LinearEasing),
////            repeatMode = RepeatMode.Reverse
////        ),
////        label = "scale1"
////    )
////    val scale2 by transition.animateFloat(
////        initialValue = 1.4f,
////        targetValue = 0.6f,
////        animationSpec = InfiniteRepeatableSpec(
////            animation = tween(7000, easing = LinearEasing),
////            repeatMode = RepeatMode.Reverse
////        ),
////        label = "scale2"
////    )
////    val opacity1 by transition.animateFloat(
////        initialValue = 0.2f,
////        targetValue = 0.5f,
////        animationSpec = InfiniteRepeatableSpec(
////            animation = tween(5000, easing = LinearEasing),
////            repeatMode = RepeatMode.Reverse
////        ),
////        label = "opacity1"
////    )
////    val opacity2 by transition.animateFloat(
////        initialValue = 0.5f,
////        targetValue = 0.2f,
////        animationSpec = InfiniteRepeatableSpec(
////            animation = tween(5500, easing = LinearEasing),
////            repeatMode = RepeatMode.Reverse
////        ),
////        label = "opacity2"
////    )
////
////    // Derive complementary colors from the base color
////    val color1 = hsvToColor(baseHue, baseSaturation * 0.8f, baseBrightness * 1.1f.coerceAtMost(1f))
////    val color2 = hsvToColor((baseHue + 0.2f) % 1f, baseSaturation, baseBrightness * 0.9f)
////    val color3 = hsvToColor((baseHue - 0.2f + 1f) % 1f, baseSaturation * 1.2f.coerceAtMost(1f), baseBrightness)
////
////    Box(modifier.fillMaxSize().background(color)) {
////        // Layer 1: Outer pulsing gradient
////        Box(
////            Modifier
////                .fillMaxSize()
////                .background(
////                    Brush.radialGradient(
////                        colors = listOf(color1.copy(alpha = opacity1), Color.Transparent),
////                        center = Offset.Infinite, // Expands from center outward
////                        radius = 800f * scale1
////                    )
////                )
////        )
////        // Layer 2: Inner counter-pulsing gradient for depth
////        Box(
////            Modifier
////                .fillMaxSize()
////                .background(
////                    Brush.radialGradient(
////                        colors = listOf(color2.copy(alpha = opacity2), color3.copy(alpha = opacity2 * 0.6f), Color.Transparent),
////                        center = Offset.Infinite,
////                        radius = 600f * scale2
////                    )
////                )
////        )
////    }
////}