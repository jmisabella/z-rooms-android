package com.jmisabella.zrooms

import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.round

@Composable
fun CustomSlider(
    value: androidx.compose.runtime.MutableState<Double>,
    minValue: Double = 0.0,
    maxValue: Double = 480.0,
    step: Double = 1.0,
    onEditingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    thumbColor: Color? = null,
    activeTrackColor: Color? = null,
    inactiveTrackColor: Color? = null
) {
    var sliderPosition by remember {
        mutableStateOf(calculateNormalized(value.value, maxValue))
    }

    LaunchedEffect(value.value) {
        sliderPosition = calculateNormalized(value.value, maxValue)
    }

    val defaultColors = SliderDefaults.colors()

    Slider(
        value = sliderPosition,
        onValueChange = { newPosition ->
            sliderPosition = newPosition
            val newValue = calculateDenormalized(newPosition, maxValue, step, minValue)
            value.value = newValue
            onEditingChanged(true)
        },
        onValueChangeFinished = {
            onEditingChanged(false)
        },
        modifier = modifier,
        colors = if (thumbColor != null || activeTrackColor != null || inactiveTrackColor != null) {
            SliderDefaults.colors(
                thumbColor = thumbColor ?: defaultColors.thumbColor(enabled = true).value,
                activeTrackColor = activeTrackColor ?: defaultColors.trackColor(enabled = true, active = true).value,
                inactiveTrackColor = inactiveTrackColor ?: defaultColors.trackColor(enabled = true, active = false).value
            )
        } else {
            defaultColors
        }
    )
}

private fun calculateNormalized(value: Double, maxValue: Double): Float {
    val exponent = 2.0
    return (value / maxValue).pow(1.0 / exponent).toFloat()
}

private fun calculateDenormalized(
    sliderValue: Float,
    maxValue: Double,
    step: Double,
    minValue: Double
): Double {
    val exponent = 2.0
    val raw = if (sliderValue == 0f) 0.0 else maxValue * sliderValue.toDouble().pow(exponent)
    val stepped = round(raw / step) * step
    return maxOf(minValue, minOf(maxValue, stepped))
}

//package com.jmisabella.zrooms
//
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.material.Slider
//import kotlin.math.pow
//import kotlin.math.round
//
//@Composable
//fun CustomSlider(
//    value: androidx.compose.runtime.MutableState<Double>, // Changed to MutableState
//    minValue: Double = 0.0,
//    maxValue: Double = 480.0,
//    step: Double = 1.0,
//    onEditingChanged: (Boolean) -> Unit = {},
//    modifier: Modifier = Modifier
//) {
//    var internalValue by remember { mutableStateOf(value.value) }
//    var isEditing by remember { mutableStateOf(false) }
//
//    val exponent = 2.0
//    val normalized = (internalValue / maxValue).pow(1.0 / exponent).toFloat()
//
//    Slider(
//        value = normalized,
//        onValueChange = { sliderValue ->
//            isEditing = true
//            onEditingChanged(true)
//            val raw = if (sliderValue == 0f) 0.0 else maxValue * sliderValue.toDouble().pow(exponent)
//            val stepped = round(raw / step) * step
//            internalValue = maxOf(minValue, minOf(maxValue, stepped))
//        },
//        onValueChangeFinished = {
//            isEditing = false
//            onEditingChanged(false)
//            value.value = internalValue  // Now valid with MutableState
//        },
//        modifier = modifier
//    )
//}