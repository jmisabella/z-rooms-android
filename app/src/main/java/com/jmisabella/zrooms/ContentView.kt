package com.jmisabella.zrooms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.preference.PreferenceManager
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import kotlin.math.min
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.compose.runtime.mutableIntStateOf

private val SelectedItemSaver: Saver<SelectedItem?, Any> = Saver(
    save = { it?.id },
    restore = { id -> if (id != null) SelectedItem(id as Int) else null }
)

@Composable
fun ContentView() {
    fun colorFor(row: Int, col: Int): Color {
        if (row <= 1) { // Top 2 rows (0, 1) for white noise
            val diag = (row + col).toFloat() / 6f // Diagonal across 2 rows * 5 cols
            val startHue = 0.67f // Blue-purple
            val endHue = 0.75f // Darker purple
            val startSat = 0.6f
            val endSat = 0.8f
            val startBright = 0.6f
            val endBright = 0.8f
            val hue = startHue + (endHue - startHue) * diag
            val sat = startSat + (endSat - startSat) * diag
            val bright = startBright + (endBright - startBright) * diag
            return hsvToColor(hue, sat, bright)
        } else if (row >= 4) { // Bottom 2 rows (4, 5) for waking audio
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
        } else { // Rows 2, 3 for nighttime music
            val origRow = row - 2
            val diag = (origRow + col).toFloat() / 6f // Diagonal across 2 rows * 5 cols
            val startHue = 0.8f // Pink-purple
            val endHue = 0.33f // Green
            val hue = startHue - (startHue - endHue) * diag
            val sat = 0.3f
            val bright = 0.9f
            return hsvToColor(hue, sat, bright)
        }
    }

    val files = (1..30).map { "ambient_%02d".format(it) }
    val context = LocalContext.current
    var selectedItem by rememberSaveable(stateSaver = SelectedItemSaver) { mutableStateOf<SelectedItem?>(null) }
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val durationState = remember { mutableStateOf(prefs.getFloat("durationMinutes", 0f).toDouble()) }
    var durationMinutes by durationState
    val alarmEnabledState = remember { mutableStateOf(prefs.getBoolean("isAlarmEnabled", false)) }
    var isAlarmEnabled by alarmEnabledState
    val alarmActiveState = remember { mutableStateOf(false) }
    var isAlarmActive by alarmActiveState
    var selectedAlarmIndex by remember { mutableIntStateOf(prefs.getInt("selectedAlarmIndex", -1)) }
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

    LaunchedEffect(durationMinutes, isAlarmEnabled, selectedAlarmIndex) {
        audioService?.updateTimer(durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
    }

    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, AudioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    DisposableEffect(Unit) {
        val broadcastManager = LocalBroadcastManager.getInstance(context)
        val alarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.jmisabella.zrooms.ALARM_STARTED" -> isAlarmActive = true
                    "com.jmisabella.zrooms.ALARM_STOPPED" -> isAlarmActive = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.jmisabella.zrooms.ALARM_STARTED")
            addAction("com.jmisabella.zrooms.ALARM_STOPPED")
        }
        broadcastManager.registerReceiver(alarmReceiver, filter)

        onDispose {
            broadcastManager.unregisterReceiver(alarmReceiver)
        }
    }

    LaunchedEffect(showingAlarmSelection) {
        println("showingAlarmSelection changed to: $showingAlarmSelection")
    }

    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = isLandscape,
        confirmValueChange = { newValue ->
            println("confirmValueChange called, newValue=$newValue")
            true
        }
    )

    LaunchedEffect(sheetState.currentValue) {
        println("sheetState.currentValue changed to: ${sheetState.currentValue}")
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            println("Sheet hidden, calling onDismiss")
            showingAlarmSelection = false
            audioService?.stopPreview()
        } else if (sheetState.currentValue != ModalBottomSheetValue.Hidden && selectedAlarmIndex >= 0) {
            println("Sheet shown, playing preview for pre-selected alarm index: $selectedAlarmIndex")
            audioService?.playPreview(selectedAlarmIndex)
        }
    }

    LaunchedEffect(selectedAlarmIndex) {
        println("selectedAlarmIndex changed to: $selectedAlarmIndex")
        if (selectedAlarmIndex >= 0 && sheetState.currentValue != ModalBottomSheetValue.Hidden) {
            audioService?.playPreview(selectedAlarmIndex)
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            AlarmSelectionContent(
                selectedAlarmIndex = selectedAlarmIndex,
                onSelect = { index ->
                    println("Alarm selected, index=$index")
                    selectedAlarmIndex = index ?: -1
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("selectedAlarmIndex", selectedAlarmIndex).apply()
                },
                files = files,
                audioService = audioService,
                isLandscape = isLandscape
            )
        }
    ) {
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
                    contentPadding = PaddingValues(top = 80.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { _, _ -> }
                        }
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
                        durationMinutes = durationState,
                        isAlarmActive = alarmActiveState,
                        isAlarmEnabled = alarmEnabledState,
                        changeRoom = { direction ->
                            if (selectedItem == null) {
                                println("No selected item, cannot change room")
                                false
                            } else {
                                val currentIndex = selectedItem!!.id
                                val newIndex = currentIndex + direction
                                if (newIndex in 0 until files.size && audioService?.isReady() == true) {
                                    selectedItem = SelectedItem(newIndex)
                                    audioService?.playAmbient(newIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
                                    true
                                } else {
                                    println("Invalid new index: $newIndex or audioService not ready")
                                    false
                                }
                            }
                        },
                        currentIndex = selected.id,
                        maxIndex = files.size,
                        selectAlarm = {
                            println("selectAlarm called from ExpandingView, setting showingAlarmSelection to true")
                            showingAlarmSelection = true
                        },
                        audioService = audioService
                    )
                }
            }

            androidx.compose.material.Text(
                text = "z rooms",
                fontSize = 14.sp,
                color = Color(0xFFB3B3B3),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
            )

            if (selectedItem == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    println("Swipe area drag started at offset: $offset, showingAlarmSelection=$showingAlarmSelection, selectedItem=$selectedItem")
                                },
                                onDragEnd = {
                                    println("Swipe area drag ended, showingAlarmSelection=$showingAlarmSelection")
                                },
                                onDragCancel = {
                                    println("Swipe area drag cancelled, showingAlarmSelection=$showingAlarmSelection")
                                }
                            ) { _, dragAmount ->
                                println("Swipe area drag detected, dragAmount=$dragAmount, showingAlarmSelection=$showingAlarmSelection, selectedItem=$selectedItem")
                                if (dragAmount.y < -10 && !showingAlarmSelection) {
                                    println("Swipe up detected in swipe area, setting showingAlarmSelection to true")
                                    showingAlarmSelection = true
                                }
                            }
                        }
                )
            }
        }
    }

    LaunchedEffect(showingAlarmSelection) {
        if (showingAlarmSelection) {
            coroutineScope.launch {
                println("Launching sheetState.show()")
                sheetState.show()
            }
        } else {
            coroutineScope.launch {
                println("Launching sheetState.hide()")
                sheetState.hide()
            }
        }
    }
}

//package com.jmisabella.zrooms
//
//import android.content.ComponentName
//import android.content.Context
//import android.content.Intent
//import android.content.ServiceConnection
//import android.content.res.Configuration
//import android.os.Build
//import android.os.IBinder
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.core.tween
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
//import androidx.compose.foundation.background
//import androidx.compose.foundation.gestures.detectDragGestures
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.BoxWithConstraints
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.windowInsetsPadding
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.foundation.lazy.grid.items
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.saveable.rememberSaveable
//import androidx.compose.runtime.saveable.Saver
//import androidx.compose.runtime.setValue
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalConfiguration
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.foundation.layout.WindowInsets
//import androidx.compose.foundation.layout.navigationBars
//import androidx.preference.PreferenceManager
//import androidx.compose.material.ModalBottomSheetLayout
//import androidx.compose.material.ModalBottomSheetValue
//import androidx.compose.material.rememberModalBottomSheetState
//import kotlinx.coroutines.launch
//import kotlin.math.min
//import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import android.content.BroadcastReceiver
//import android.content.IntentFilter
//import androidx.compose.runtime.mutableIntStateOf
//
//private val SelectedItemSaver: Saver<SelectedItem?, Any> = Saver(
//    save = { it?.id },
//    restore = { id -> if (id != null) SelectedItem(id as Int) else null }
//)
//
//@Composable
//fun ContentView() {
//    fun colorFor(row: Int, col: Int): Color {
//        if (row == 0) {
//            val hue = 0.67f // Blue
//            val saturation = 0.6f
//            val diag = col.toFloat() / 4f
//            val startBright = 0.6f
//            val endBright = 0.8f
//            val brightness = startBright + (endBright - startBright) * diag
//            return hsvToColor(hue, saturation, brightness)
//        } else if (row >= 4) {
//            val origRow = row - 4
//            val diag = (origRow + col).toFloat() / 4f
//            val startHue = 0f // Light grey
//            val endHue = 0.083f // Muted orange
//            val startSat = 0.1f
//            val endSat = 0.6f
//            val startBright = 0.9f
//            val endBright = 0.7f
//            val hue = startHue + (endHue - startHue) * diag
//            val sat = startSat + (endSat - startSat) * diag
//            val bright = startBright - (startBright - endBright) * diag
//            return hsvToColor(hue, sat, bright)
//        } else {
//            val origRow = row - 1
//            val diag = (origRow + col).toFloat() / 8f
//            val startHue = 0.8f
//            val endHue = 0.33f
//            val hue = startHue - (startHue - endHue) * diag
//            val sat = 0.3f
//            val bright = 0.9f
//            return hsvToColor(hue, sat, bright)
//        }
//    }
//
//    val files = (1..30).map { "ambient_%02d".format(it) }
//    val context = LocalContext.current
//    var selectedItem by rememberSaveable(stateSaver = SelectedItemSaver) { mutableStateOf<SelectedItem?>(null) }
//    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
//    val durationState = remember { mutableStateOf(prefs.getFloat("durationMinutes", 0f).toDouble()) }
//    var durationMinutes by durationState
//    val alarmEnabledState = remember { mutableStateOf(prefs.getBoolean("isAlarmEnabled", false)) }
//    var isAlarmEnabled by alarmEnabledState
//    val alarmActiveState = remember { mutableStateOf(false) }
//    var isAlarmActive by alarmActiveState
//    var selectedAlarmIndex by remember { mutableIntStateOf(prefs.getInt("selectedAlarmIndex", -1)) }
//    var showingAlarmSelection by remember { mutableStateOf(false) }
//
//    var audioService by remember { mutableStateOf<AudioService?>(null) }
//    val connection = remember {
//        object : ServiceConnection {
//            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//                audioService = (service as AudioService.AudioBinder).getService()
//            }
//
//            override fun onServiceDisconnected(name: ComponentName?) {
//                audioService = null
//            }
//        }
//    }
//
//    LaunchedEffect(durationMinutes, isAlarmEnabled, selectedAlarmIndex) {
//        audioService?.updateTimer(durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
//    }
//
//    DisposableEffect(Unit) {
//        val serviceIntent = Intent(context, AudioService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            context.startForegroundService(serviceIntent)
//        } else {
//            context.startService(serviceIntent)
//        }
//
//        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
//
//        onDispose {
//            context.unbindService(connection)
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val broadcastManager = LocalBroadcastManager.getInstance(context)
//        val alarmReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                when (intent?.action) {
//                    "com.jmisabella.zrooms.ALARM_STARTED" -> isAlarmActive = true
//                    "com.jmisabella.zrooms.ALARM_STOPPED" -> isAlarmActive = false
//                }
//            }
//        }
//        val filter = IntentFilter().apply {
//            addAction("com.jmisabella.zrooms.ALARM_STARTED")
//            addAction("com.jmisabella.zrooms.ALARM_STOPPED")
//        }
//        broadcastManager.registerReceiver(alarmReceiver, filter)
//
//        onDispose {
//            broadcastManager.unregisterReceiver(alarmReceiver)
//        }
//    }
//
//    LaunchedEffect(showingAlarmSelection) {
//        println("showingAlarmSelection changed to: $showingAlarmSelection")
//    }
//
//    val coroutineScope = rememberCoroutineScope()
//    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
//    val sheetState = rememberModalBottomSheetState(
//        initialValue = ModalBottomSheetValue.Hidden,
//        skipHalfExpanded = isLandscape,
//        confirmValueChange = { newValue ->
//            println("confirmValueChange called, newValue=$newValue")
//            true
//        }
//    )
//
//    LaunchedEffect(sheetState.currentValue) {
//        println("sheetState.currentValue changed to: ${sheetState.currentValue}")
//        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
//            println("Sheet hidden, calling onDismiss")
//            showingAlarmSelection = false
//            audioService?.stopPreview()
//        } else if (sheetState.currentValue != ModalBottomSheetValue.Hidden && selectedAlarmIndex >= 0) {
//            println("Sheet shown, playing preview for pre-selected alarm index: $selectedAlarmIndex")
//            audioService?.playPreview(selectedAlarmIndex)
//        }
//    }
//
//    LaunchedEffect(selectedAlarmIndex) {
//        println("selectedAlarmIndex changed to: $selectedAlarmIndex")
//        if (selectedAlarmIndex >= 0 && sheetState.currentValue != ModalBottomSheetValue.Hidden) {
//            audioService?.playPreview(selectedAlarmIndex)
//        }
//    }
//
//    ModalBottomSheetLayout(
//        sheetState = sheetState,
//        sheetContent = {
//            AlarmSelectionContent(
//                selectedAlarmIndex = selectedAlarmIndex,
//                onSelect = { index ->
//                    println("Alarm selected, index=$index")
//                    selectedAlarmIndex = index ?: -1
//                    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("selectedAlarmIndex", selectedAlarmIndex).apply()
//                },
//                files = files,
//                audioService = audioService,
//                isLandscape = isLandscape
//            )
//        }
//    ) {
//        Box(Modifier.fillMaxSize()) {
//            Box(
//                Modifier
//                    .fillMaxSize()
//                    .background(
//                        brush = Brush.linearGradient(
//                            colors = listOf(Color(0xFF262626), Color(0xFF4D4D4D)),
//                            start = Offset(0f, 0f),
//                            end = Offset(0f, Float.POSITIVE_INFINITY)
//                        )
//                    )
//            )
//
//            BoxWithConstraints {
//                val availW = maxWidth - (20.dp * 2)
//                val availH = maxHeight - (20.dp * 2)
//                val numCols = 5
//                val numRows = 6
//                val spacing = 10.dp
//                val itemW = (availW - (spacing * (numCols - 1))) / numCols
//                val maxItemH = (availH - (spacing * (numRows - 1))) / numRows
//                val itemH = minOf(itemW, maxItemH)
//                val aspect = (itemW.value / itemH.value)
//
//                LazyVerticalGrid(
//                    columns = GridCells.Fixed(5),
//                    contentPadding = PaddingValues(top = 80.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
//                    horizontalArrangement = Arrangement.spacedBy(10.dp),
//                    verticalArrangement = Arrangement.spacedBy(10.dp),
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .pointerInput(Unit) {
//                            detectDragGestures { _, _ -> }
//                        }
//                ) {
//                    items(30) { index ->
//                        AlarmItemView(
//                            index = index,
//                            aspect = aspect,
//                            colorFor = ::colorFor,
//                            files = files,
//                            selectedItem = selectedItem,
//                            onSelect = { selectedIndex ->
//                                selectedItem = SelectedItem(selectedIndex)
//                                audioService?.playAmbient(selectedIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
//                            }
//                        )
//                    }
//                }
//            }
//
//
//            AnimatedVisibility(
//                visible = selectedItem != null,
//                enter = fadeIn(animationSpec = tween(300)),
//                exit = fadeOut(animationSpec = tween(300))
//            ) {
//                selectedItem?.let { selected ->
//                    val row = selected.id / 5
//                    val col = selected.id % 5
//                    val color = colorFor(row, col)
//
//                    ExpandingView(
//                        color = color,
//                        dismiss = {
//                            selectedItem = null
//                            isAlarmActive = false
//                            audioService?.stopAll()
//                        },
//                        durationMinutes = durationState,
//                        isAlarmActive = alarmActiveState,
//                        isAlarmEnabled = alarmEnabledState,
//                        changeRoom = { direction ->
//                            if (selectedItem == null) {
//                                println("No selected item, cannot change room")
//                                false
//                            } else {
//                                val currentIndex = selectedItem!!.id
//                                val newIndex = currentIndex + direction
//                                if (newIndex in 0 until files.size && audioService?.isReady() == true) {
//                                    selectedItem = SelectedItem(newIndex)
//                                    audioService?.playAmbient(newIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
//                                    true
//                                } else {
//                                    println("Invalid new index: $newIndex or audioService not ready")
//                                    false
//                                }
//                            }
//                        },
//                        currentIndex = selected.id,
//                        maxIndex = files.size,
//                        selectAlarm = {
//                            println("selectAlarm called from ExpandingView, setting showingAlarmSelection to true")
//                            showingAlarmSelection = true
//                        },
//                        audioService = audioService
//                    )
//                }
//            }
//
//            androidx.compose.material.Text(
//                text = "z rooms",
//                fontSize = 14.sp,
//                color = Color(0xFFB3B3B3),
//                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
//            )
//
//            if (selectedItem == null) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(300.dp)
//                        .align(Alignment.BottomCenter)
//                        .windowInsetsPadding(WindowInsets.navigationBars)
//                        .pointerInput(Unit) {
//                            detectDragGestures(
//                                onDragStart = { offset ->
//                                    println("Swipe area drag started at offset: $offset, showingAlarmSelection=$showingAlarmSelection, selectedItem=$selectedItem")
//                                },
//                                onDragEnd = {
//                                    println("Swipe area drag ended, showingAlarmSelection=$showingAlarmSelection")
//                                },
//                                onDragCancel = {
//                                    println("Swipe area drag cancelled, showingAlarmSelection=$showingAlarmSelection")
//                                }
//                            ) { _, dragAmount ->
//                                println("Swipe area drag detected, dragAmount=$dragAmount, showingAlarmSelection=$showingAlarmSelection, selectedItem=$selectedItem")
//                                if (dragAmount.y < -10 && !showingAlarmSelection) {
//                                    println("Swipe up detected in swipe area, setting showingAlarmSelection to true")
//                                    showingAlarmSelection = true
//                                }
//                            }
//                        }
//                )
//            }
//        }
//    }
//
//    LaunchedEffect(showingAlarmSelection) {
//        if (showingAlarmSelection) {
//            coroutineScope.launch {
//                println("Launching sheetState.show()")
//                sheetState.show()
//            }
//        } else {
//            coroutineScope.launch {
//                println("Launching sheetState.hide()")
//                sheetState.hide()
//            }
//        }
//    }
//}

