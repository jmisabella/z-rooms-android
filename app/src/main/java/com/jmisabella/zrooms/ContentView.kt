package com.jmisabella.zrooms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.preference.PreferenceManager
import com.jmisabela.zrooms.AlarmSelectionView

@Composable
fun ContentView() {
    fun colorFor(row: Int, col: Int): Color {
        if (row == 0) {
            val hue = 0.67f // Blue
            val saturation = 0.6f
            val diag = col.toFloat() / 4f
            val startBright = 0.6f
            val endBright = 0.8f
            val brightness = startBright + (endBright - startBright) * diag
            return hsvToColor(hue, saturation, brightness)
        } else if (row >= 4) {
            val origRow = row - 4
            val diag = (origRow + col).toFloat() / 4f
            val startHue = 0f // Light grey
            val endHue = 0.083f // Muted orange
            val startSat = 0.1f
            val endSat = 0.6f
            val startBright = 0.9f
            val endBright = 0.7f
            val hue = startHue + (endHue - startHue) * diag
            val sat = startSat + (endSat - startSat) * diag
            val bright = startBright - (startBright - endBright) * diag
            return hsvToColor(hue, sat, bright)
        } else {
            val origRow = row - 1
            val diag = (origRow + col).toFloat() / 8f
            val startHue = 0.8f
            val endHue = 0.33f
            val hue = startHue - (startHue - endHue) * diag
            val sat = 0.3f
            val bright = 0.9f
            return hsvToColor(hue, sat, bright)
        }
    }

    val files = (1..30).map { "ambient_%02d".format(it) }
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<SelectedItem?>(null) }
    var durationMinutes by remember { mutableStateOf(PreferenceManager.getDefaultSharedPreferences(context).getFloat("durationMinutes", 0f).toDouble()) }
    var isAlarmEnabled by remember { mutableStateOf(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("isAlarmEnabled", false)) }
    var isAlarmActive by remember { mutableStateOf(false) }
    var selectedAlarmIndex by remember { mutableStateOf(PreferenceManager.getDefaultSharedPreferences(context).getInt("selectedAlarmIndex", -1)) }
    var showingAlarmSelection by remember { mutableStateOf(false) }

    var audioService by remember { mutableStateOf<AudioService?>(null) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioService = (service as AudioService.AudioBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audioService = null
            }
        }
    }

    DisposableEffect(Unit) {
        context.bindService(Intent(context, AudioService::class.java), connection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(connection) }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF262626), Color(0xFF4D4D4D)),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        )

        BoxWithConstraints {
            val availW = maxWidth - (20.dp * 2)
            val availH = maxHeight - (20.dp * 2)
            val numCols = 5
            val numRows = 6
            val spacing = 10.dp
            val itemW = (availW - (spacing * (numCols - 1))) / numCols
            val maxItemH = (availH - (spacing * (numRows - 1))) / numRows
            val itemH = minOf(itemW, maxItemH)
            val aspect = (itemW.value / itemH.value)

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(30) { index ->
                    AlarmItemView(
                        index = index,
                        aspect = aspect,
                        colorFor = ::colorFor,
                        files = files,
                        selectedItem = selectedItem,
                        onSelect = { selectedIndex ->
                            selectedItem = SelectedItem(selectedIndex)
                            audioService?.playAmbient(selectedIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedItem != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            selectedItem?.let { selected ->
                val row = selected.id / 5
                val col = selected.id % 5
                val color = colorFor(row, col)

                ExpandingView(
                    color = color,
                    dismiss = {
                        selectedItem = null
                        isAlarmActive = false
                        audioService?.stopAll()
                    },
                    durationMinutes = mutableStateOf(durationMinutes),
                    isAlarmActive = mutableStateOf(isAlarmActive),
                    isAlarmEnabled = mutableStateOf(isAlarmEnabled),
                    changeRoom = { direction ->
                        val currentIndex = selected.id
                        var newIndex = currentIndex + direction
                        while (newIndex in 0 until files.size && files[newIndex].isEmpty()) {
                            newIndex += direction
                        }
                        if (newIndex in 0 until files.size) {
                            selectedItem = SelectedItem(newIndex)
                            audioService?.playAmbient(newIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
                        }
                    },
                    currentIndex = selected.id,
                    maxIndex = files.size,
                    selectAlarm = { showingAlarmSelection = true }
                )
            }
        }

        androidx.compose.material.Text(
            text = "z rooms",
            fontSize = 14.sp,
            color = Color(0xFFB3B3B3),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        )

        if (showingAlarmSelection) {
            AlarmSelectionView(
                selectedAlarmIndex = selectedAlarmIndex,
                onSelect = { index ->
                    selectedAlarmIndex = index ?: -1
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("selectedAlarmIndex", selectedAlarmIndex).apply()
                    showingAlarmSelection = false
                },
                files = files,
                fadeMainTo = { target ->
                    audioService?.fadeAmbientVolume(target, 2000L)
                }
            )
        }
    }
}