package com.jmisabela.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AlarmSelectionView(
    selectedAlarmIndex: Int?,
    onSelect: (Int?) -> Unit,
    files: List<String>,
    fadeMainTo: (Float) -> Unit
) {
    val context = LocalContext.current
    var previewVolume by remember { mutableStateOf(0f) }
    // Note: You'll need to bind to AudioService here for preview playback, similar to ContentView

    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var showSheet by remember { mutableStateOf(true) } // Set to true to show modal by default, adjust as needed

    // Define alarmColorFor before using it
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

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {  // Near-black
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val alarmIndices = (20 until 30) + (15 until 20)
                    itemsIndexed(alarmIndices) { i, index ->
                        val row = i / 5
                        val col = i % 5
                        val isSelected = selectedAlarmIndex == index
                        val color = alarmColorFor(row, col, isSelected)

                        Box(
                            Modifier
                                .background(color, RoundedCornerShape(8.dp))
                                .border(
                                    if (isSelected) 4.dp else 0.dp,
                                    Color.White,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        onSelect(null)
                                        fadeMainTo(1f)
                                        // Stop preview via AudioService
                                    } else {
                                        onSelect(index)
                                        // Play preview via AudioService
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
    ) {
        // Trigger to show sheet, e.g., a button or LaunchedEffect
        LaunchedEffect(Unit) {
            sheetState.show()
        }
    }

    // onAppear equivalent
    LaunchedEffect(selectedAlarmIndex) {
        if (selectedAlarmIndex != null) {
            // Play preview via AudioService
            // Example: audioService?.playPreview(files[selectedAlarmIndex])
        }
    }

    // onDisappear equivalent (cleanup)
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible) {
            // Stop preview via AudioService
            fadeMainTo(1f)
        }
    }
}

// Helper: HSV to Color (ensure this is defined in a util file or here)
fun hsvToColor(hue: Float, sat: Float, value: Float): Color {
    val hsv = floatArrayOf(hue * 360f, sat, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}