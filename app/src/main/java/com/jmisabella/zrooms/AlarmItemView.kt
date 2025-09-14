package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AlarmItemView(
    index: Int,
    aspect: Float,
    colorFor: (Int, Int) -> Color,
    files: List<String>,
    selectedItem: SelectedItem?,
    onSelect: (Int) -> Unit
) {
    val row = index / 5
    val col = index % 5
    val color = colorFor(row, col)
    val file = if (index < files.size) files[index] else ""
    val isDisabled = file.isEmpty() || selectedItem != null

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(100.dp)
            .aspectRatio(aspect)
            .background(color, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = !isDisabled) {
                if (!file.isEmpty()) {
                    onSelect(index)
                }
            }
            .then(if (selectedItem?.id == index) Modifier.alpha(0f) else Modifier)
    )
}