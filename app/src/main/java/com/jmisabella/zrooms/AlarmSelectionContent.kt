package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

@Composable
fun AlarmSelectionContent(
    selectedAlarmIndex: Int?,
    onSelect: (Int?) -> Unit,
    files: List<String>,
    audioService: AudioService?,
    isLandscape: Boolean
) {
    BoxWithConstraints {
        val sheetHeight = if (isLandscape) maxHeight else maxHeight / 2.2f
        val numCols = 5
        val numRows = 4 // Changed to 4 to accommodate the new silence row
        val spacing = 10.dp
        val gridPadding = if (isLandscape) PaddingValues(10.dp) else PaddingValues(20.dp)
        val textBottomPadding = if (isLandscape) 20.dp else 40.dp
        val layoutDirection = LocalLayoutDirection.current
        val totalHorizontalPadding = gridPadding.calculateLeftPadding(layoutDirection) +
                gridPadding.calculateRightPadding(layoutDirection)
        val totalVerticalPadding = gridPadding.calculateTopPadding() +
                gridPadding.calculateBottomPadding()
        val availW = maxWidth - totalHorizontalPadding - (spacing * (numCols - 1))
        val buffer = textBottomPadding // Buffer to prevent overlap with bottom text
        val availH = sheetHeight - totalVerticalPadding - (spacing * (numRows - 1)) - buffer
        // Always calculate tileSize to ensure fit in both orientations
        val tileSize = min(availW.value / numCols, availH.value / numRows).dp

        Box(
            Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .background(Color(0xFF1A1A1A))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(numCols),
                contentPadding = gridPadding,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize()
            ) {
                val alarmIndices = (20 until 30) + (15 until 20)
                itemsIndexed(alarmIndices) { i, index ->
                    val row = i / 5
                    val col = i % 5
                    val isSelected = selectedAlarmIndex == index
                    val color = alarmColorFor(row, col, isSelected)

                    Box(
                        Modifier
                            .size(tileSize) // Use calculated tileSize for square tiles
                            .background(color, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 4.dp else 0.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
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
                // Add the silence row as a spanning item
                item(span = { GridItemSpan(numCols) }) {
                    val isSilenceSelected = selectedAlarmIndex == null || (selectedAlarmIndex ?: -1) < 0
                    val silenceColor = if (isSilenceSelected) Color(0xFFfbf7f5) else Color(0xFFbdbdbd) // Brighter on select to match iOS contrast
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(tileSize)
                            .background(silenceColor, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSilenceSelected) 4.dp else 0.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onSelect(null)
                                    audioService?.stopPreview()
                                }
                            }
                    ) {
                        Text(
                            text = "silence",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Thin,
                            color = Color(0xFF696969), // Lighter text for visibility on darker backgrounds
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            Text(
                text = "waking rooms",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF808080),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) textBottomPadding else textBottomPadding + 12.dp)
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

