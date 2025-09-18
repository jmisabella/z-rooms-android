package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import kotlin.math.abs

@Composable
fun AlarmSelectionContent(
    selectedAlarmIndex: Int?,
    onSelect: (Int?) -> Unit,
    files: List<String>,
    audioService: AudioService?
) {
    BoxWithConstraints {  // Added to access maxHeight for screen size
        val sheetHeight = maxHeight / 3  // ~1/3 of screen height
        Box(
            Modifier
                .fillMaxWidth()
                .height(sheetHeight)  // Constrain to 1/3 height (replaces wrapContentHeight)
                .background(Color(0xFF1A1A1A))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()  // Now fills the constrained 1/3 height
            ) {
                val alarmIndices = (20 until 30) + (15 until 20)
                itemsIndexed(alarmIndices) { i, index ->
                    val row = i / 5
                    val col = i % 5
                    val isSelected = selectedAlarmIndex == index
                    val color = alarmColorFor(row, col, isSelected)

                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .background(color, RoundedCornerShape(8.dp))
                            .border(
                                if (isSelected) 4.dp else 0.dp,
                                Color.White,
                                RoundedCornerShape(8.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    println("Alarm tile tapped at offset: $offset, index=$index, isSelected=$isSelected")
                                    if (isSelected) {
                                        onSelect(null)
                                        audioService?.stopPreview()
                                    } else {
                                        onSelect(index)
                                        audioService?.playPreview(index)
                                    }
                                }
                            }
                    )
                }
            }
            Text(
                text = "waking rooms",
                fontSize = 14.sp,
                color = Color(0xFF808080),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }
    }
}

fun alarmColorFor(row: Int, col: Int, isSelected: Boolean): Color {
    if (row < 2) {
        val diag = (row.toFloat() + col.toFloat()) / 8f
        var startHue = 0.166f  // Yellow
        var endHue = 0.916f    // Pink
        var delta = endHue - startHue
        if (abs(delta) > 0.5f) {
            delta -= if (delta > 0) 1f else -1f
        }
        var hue = startHue + delta * diag
        if (hue < 0) hue += 1f else if (hue > 1) hue -= 1f
        val saturation = 0.8f
        val brightness = if (isSelected) 0.9f else 0.9f * 0.5f
        return hsvToColor(hue, saturation, brightness)
    } else {
        val progress = col.toFloat() / 4f
        val startHue = 0.666f  // Deep blue
        val endHue = 0.833f    // Deep purple
        val hue = startHue + (endHue - startHue) * progress
        val saturation = 0.8f
        val brightness = if (isSelected) 0.6f else 0.6f * 0.5f
        return hsvToColor(hue, saturation, brightness)
    }
}

//package com.jmisabella.zrooms
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.aspectRatio
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.wrapContentHeight
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.foundation.lazy.grid.itemsIndexed
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.foundation.gestures.detectTapGestures
//import kotlin.math.abs
//
//@Composable
//fun AlarmSelectionContent(
//    selectedAlarmIndex: Int?,
//    onSelect: (Int?) -> Unit,
//    files: List<String>,
//    audioService: AudioService?
//) {
//    Box(Modifier.fillMaxWidth().wrapContentHeight().background(Color(0xFF1A1A1A))) {
//        LazyVerticalGrid(
//            columns = GridCells.Fixed(5),
//            contentPadding = PaddingValues(20.dp),
//            horizontalArrangement = Arrangement.spacedBy(10.dp),
//            verticalArrangement = Arrangement.spacedBy(10.dp),
//            modifier = Modifier.fillMaxSize()
//        ) {
//            val alarmIndices = (20 until 30) + (15 until 20)
//            itemsIndexed(alarmIndices) { i, index ->
//                val row = i / 5
//                val col = i % 5
//                val isSelected = selectedAlarmIndex == index
//                val color = alarmColorFor(row, col, isSelected)
//
//                Box(
//                    Modifier
//                        .aspectRatio(1f)
//                        .background(color, RoundedCornerShape(8.dp))
//                        .border(
//                            if (isSelected) 4.dp else 0.dp,
//                            Color.White,
//                            RoundedCornerShape(8.dp)
//                        )
//                        .pointerInput(Unit) {
//                            detectTapGestures { offset ->
//                                println("Alarm tile tapped at offset: $offset, index=$index, isSelected=$isSelected")
//                                if (isSelected) {
//                                    onSelect(null)
//                                    audioService?.stopPreview()
//                                } else {
//                                    onSelect(index)
//                                    audioService?.playPreview(index)
//                                }
//                            }
//                        }
//                )
//            }
//        }
//        Text(
//            text = "waking rooms",
//            fontSize = 14.sp,
//            color = Color(0xFF808080),
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 40.dp)
//        )
//    }
//}
//
//fun alarmColorFor(row: Int, col: Int, isSelected: Boolean): Color {
//    if (row < 2) {
//        val diag = (row.toFloat() + col.toFloat()) / 8f
//        var startHue = 0.166f  // Yellow
//        var endHue = 0.916f    // Pink
//        var delta = endHue - startHue
//        if (abs(delta) > 0.5f) {
//            delta -= if (delta > 0) 1f else -1f
//        }
//        var hue = startHue + delta * diag
//        if (hue < 0) hue += 1f else if (hue > 1) hue -= 1f
//        val saturation = 0.8f
//        val brightness = if (isSelected) 0.9f else 0.9f * 0.5f
//        return hsvToColor(hue, saturation, brightness)
//    } else {
//        val progress = col.toFloat() / 4f
//        val startHue = 0.666f  // Deep blue
//        val endHue = 0.833f    // Deep purple
//        val hue = startHue + (endHue - startHue) * progress
//        val saturation = 0.8f
//        val brightness = if (isSelected) 0.6f else 0.6f * 0.5f
//        return hsvToColor(hue, saturation, brightness)
//    }
//}