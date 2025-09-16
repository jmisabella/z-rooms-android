package com.jmisabella.zrooms

import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.pow
import kotlin.math.round

@Composable
fun CustomSlider(
    value: androidx.compose.runtime.MutableState<Double>,
    minValue: Double = 0.0,
    maxValue: Double = 480.0,
    step: Double = 1.0,
    onEditingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember {
        mutableStateOf(calculateNormalized(value.value, maxValue))
    }

    LaunchedEffect(value.value) {
        sliderPosition = calculateNormalized(value.value, maxValue)
    }

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
        modifier = modifier
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