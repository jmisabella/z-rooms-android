package com.jmisabella.zrooms

import androidx.compose.ui.graphics.Color

fun hsvToColor(hue: Float, sat: Float, value: Float): Color {
    val hsv = floatArrayOf(hue * 360f, sat, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}