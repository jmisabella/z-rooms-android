package com.jmisabella.zrooms

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.toArgb

@Composable
fun BreathingBackground(color: Color, modifier: Modifier = Modifier) {
    val hsva = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsva)
    val baseHue = hsva[0] / 360f
    val baseSaturation = hsva[1]
    val baseBrightness = hsva[2]

    val transition = rememberInfiniteTransition(label = "plasma")

    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100000000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Rely on external dim overlay for fading
    val fadeOpacity = 1.0f

    // Derive a 2-3 color palette from the base color
    val color1 = hsvToColor(baseHue, baseSaturation * 0.95f, baseBrightness * 1.05f.coerceAtMost(1f))
    val color2 = hsvToColor((baseHue + 0.1f).mod(1f), baseSaturation * 1.05f.coerceAtMost(1f), baseBrightness * 0.95f)
    val color3 = hsvToColor((baseHue - 0.1f + 1f).mod(1f), baseSaturation * 0.9f, baseBrightness * 1.1f.coerceAtMost(1f))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(PLASMA_SHADER) }

        Canvas(modifier = modifier.fillMaxSize()) {
            shader.setFloatUniform("time", time * 0.05f)
            shader.setFloatUniform("fadeOpacity", fadeOpacity)
            shader.setFloatUniform("resolution", size.width, size.height)
            // Normalize color components to [0, 1]
            shader.setFloatUniform("color1", color1.red, color1.green, color1.blue, color1.alpha)
            shader.setFloatUniform("color2", color2.red, color2.green, color2.blue, color2.alpha)
            shader.setFloatUniform("color3", color3.red, color3.green, color3.blue, color3.alpha)

            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        // Fallback for older Android versions: solid color background
        Box(modifier = modifier
            .fillMaxSize()
            .background(color)
        )
    }
}

private const val PLASMA_SHADER = """
    uniform float time;
    uniform float fadeOpacity;
    uniform vec2 resolution;
    uniform vec4 color1;
    uniform vec4 color2;
    uniform vec4 color3;

    half4 main(vec2 fragCoord) {
        vec2 uv = fragCoord / resolution;

        float v = 0.0;
        v += sin((uv.x * 8.0) + time);
        v += sin((uv.y * 8.0) + time * 0.7);
        v += sin((uv.x + uv.y) * 4.0 + time * 0.4);
        v += sin(length(uv - vec2(0.5)) * 12.0 + time * 0.3);
        v /= 4.0;
        v = sin(v * 3.14159) * 0.5 + 0.5;

        vec3 col;
        if (v < 0.5) {
            col = mix(color1.rgb, color2.rgb, v * 2.0);
        } else {
            col = mix(color2.rgb, color3.rgb, (v - 0.5) * 2.0);
        }

        col *= fadeOpacity;

        return half4(col, 1.0);
    }
"""

