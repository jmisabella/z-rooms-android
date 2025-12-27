package com.jmisabella.zrooms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import kotlin.math.min

private val SelectedItemSaver: Saver<SelectedItem?, Any> = Saver(
    save = { it?.id },
    restore = { id -> if (id != null) SelectedItem(id as Int) else null }
)

@Composable
fun ContentView() {
    fun colorFor(row: Int, col: Int): Color {
        val diag = (row + col).toFloat() / 10f // Updated for 7 rows
        if (row <= 1) {
            val startHue = 0.67f // Dark purple
            val endHue = 0.75f // Lighter purple
            val startSat = 0.7f
            val endSat = 0.5f
            val startBright = 0.5f
            val endBright = 0.7f
            val hue = startHue + (endHue - startHue) * diag
            val sat = startSat - (startSat - endSat) * diag
            val bright = startBright + (endBright - startBright) * diag
            return hsvToColor(hue, sat, bright)
        } else if (row <= 3) {
            val origRow = row - 2
            val diag = (origRow + col).toFloat() / 6f
            val startHue = 0.8f // Pink
            val endHue = 0.33f // Green
            val hue = startHue - (startHue - endHue) * diag
            val sat = 0.3f
            val bright = 0.9f
            return hsvToColor(hue, sat, bright)
        } else if (row <= 5) {
            val origRow = row - 4
            val diag = (origRow + col).toFloat() / 5f
            val startHue = 0.0f // Light grey
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
            // row 6: White to soft yellow
            val progress = col.toFloat() / 4f
            val hue = 0.166f // Yellow
            val startSat = 0.0f // White
            val endSat = 0.3f // Soft yellow
            val sat = startSat + (endSat - startSat) * progress
            val startBright = 0.75f // yellow-white
            val endBright = 0.65f // Soft yellow
            val bright = startBright - (startBright - endBright) * progress
            return hsvToColor(hue, sat, bright)
        }
    }

    val files = (1..35).map { "ambient_%02d".format(it) }
    val context = LocalContext.current
    var selectedItem by rememberSaveable(stateSaver = SelectedItemSaver) { mutableStateOf<SelectedItem?>(null) }
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val durationState = remember { mutableStateOf(prefs.getFloat("durationMinutes", 0f).toDouble()) }
    var durationMinutes by durationState
    val alarmEnabledState = remember { mutableStateOf(prefs.getBoolean("isAlarmEnabled", true)) }
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

    LaunchedEffect(durationMinutes, selectedAlarmIndex) {
        if (durationMinutes > 0 && selectedAlarmIndex >= 0 && !isAlarmEnabled) {
            isAlarmEnabled = true
            prefs.edit().putBoolean("isAlarmEnabled", true).apply()
        }
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


    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bottomLabelPadding = if (isLandscape) 40.dp else 60.dp
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = isLandscape,
        confirmValueChange = { _ ->
            true
        }
    )

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            showingAlarmSelection = false
            audioService?.stopPreview()
        } else if (sheetState.currentValue != ModalBottomSheetValue.Hidden && selectedAlarmIndex >= 0) {
            audioService?.playPreview(selectedAlarmIndex)
        }
    }

    LaunchedEffect(selectedAlarmIndex) {
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

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val availW = maxWidth - (20.dp * 2)
                val availH = maxHeight - (20.dp * 2)
                val numCols = 5
                val numRows = 7 // Updated to 7 rows
                val spacing = 10.dp
                val itemW = (availW - (spacing * (numCols - 1))) / numCols
                val maxItemH = (availH - (spacing * (numRows - 1))) / numRows
                val itemH = minOf(itemW, maxItemH).coerceAtLeast(48.dp) // Ensure minimum tappable size
                val aspect = (itemW.value / itemH.value)

                androidx.compose.runtime.key(configuration.orientation) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(
                            top = if (isLandscape) 0.dp else 80.dp,
                            start = 20.dp,
                            end = 20.dp,
                            bottom = if (isLandscape) 60.dp else 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        items(35) { index -> // Updated to 35 items
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
                                false
                            } else {
                                val currentIndex = selectedItem!!.id
                                val newIndex = currentIndex + direction
                                if (newIndex in 0 until files.size) {
                                    selectedItem = SelectedItem(newIndex)
                                    audioService?.playAmbient(newIndex, durationMinutes, isAlarmEnabled, selectedAlarmIndex.takeIf { it >= 0 })
                                    true
                                } else {
                                    false
                                }
                            }
                        },
                        currentIndex = selected.id,
                        maxIndex = files.size,
                        selectAlarm = {
                            showingAlarmSelection = true
                        },
                        audioService = audioService
                    )
                }
            }

            if (selectedItem == null) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomLabelPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "z rooms",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB3B3B3)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("swipe ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("up")
                            }
                            append(" from any screen for ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("waking rooms")
                            }
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFB3B3B3)
                    )
                }
            }

            if (selectedItem == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 100.dp else 200.dp)
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { _ ->
                                },
                                onDragEnd = {
                                },
                                onDragCancel = {
                                }
                            ) { _, dragAmount ->
                                if (dragAmount.y < -10 && !showingAlarmSelection) {
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
                sheetState.show()
            }
        } else {
            coroutineScope.launch {
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
//import androidx.compose.foundation.layout.Column
//import androidx.compose.ui.text.AnnotatedString
//import androidx.compose.ui.text.SpanStyle
//import androidx.compose.ui.text.buildAnnotatedString
//import androidx.compose.ui.text.withStyle
//import androidx.compose.foundation.layout.Spacer
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
//import androidx.compose.material.Text
//import androidx.compose.runtime.mutableIntStateOf
//import androidx.compose.ui.text.font.FontWeight
//
//private val SelectedItemSaver: Saver<SelectedItem?, Any> = Saver(
//    save = { it?.id },
//    restore = { id -> if (id != null) SelectedItem(id as Int) else null }
//)
//
//@Composable
//fun ContentView() {
//    fun colorFor(row: Int, col: Int): Color {
//        if (row <= 1) { // Top 2 rows (0, 1) for white noise
//            val diag = (row + col).toFloat() / 6f // Diagonal across 2 rows * 5 cols
//            val startHue = 0.67f // Blue-purple
//            val endHue = 0.75f // Darker purple
//            val startSat = 0.6f
//            val endSat = 0.8f
//            val startBright = 0.6f
//            val endBright = 0.8f
//            val hue = startHue + (endHue - startHue) * diag
//            val sat = startSat + (endSat - startSat) * diag
//            val bright = startBright + (endBright - startBright) * diag
//            return hsvToColor(hue, sat, bright)
//        } else if (row >= 4) { // Bottom 2 rows (4, 5) for waking audio
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
//        } else { // Rows 2, 3 for nighttime music
//            val origRow = row - 2
//            val diag = (origRow + col).toFloat() / 6f // Diagonal across 2 rows * 5 cols
//            val startHue = 0.8f // Pink-purple
//            val endHue = 0.33f // Green
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
//    //val alarmEnabledState = remember { mutableStateOf(prefs.getBoolean("isAlarmEnabled", false)) }
//    val alarmEnabledState = remember { mutableStateOf(prefs.getBoolean("isAlarmEnabled", true)) }
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
//    // ADDED FOR BUG FIX: Automatically enable alarm if duration > 0 and a valid alarm sound (not silence) is selected
//    LaunchedEffect(durationMinutes, selectedAlarmIndex) {
//        if (durationMinutes > 0 && selectedAlarmIndex >= 0 && !isAlarmEnabled) {
//            isAlarmEnabled = true
//            prefs.edit().putBoolean("isAlarmEnabled", true).apply()
//        }
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
//    val bottomLabelPadding = if (isLandscape) 40.dp else 60.dp
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
////                val itemH = minOf(itemW, maxItemH)
//                val itemH = minOf(itemW, maxItemH).coerceAtLeast(48.dp) // Ensure minimum tappable size
//                val aspect = (itemW.value / itemH.value)
//
//                LazyVerticalGrid(
//                    columns = GridCells.Fixed(5),
//                    contentPadding = PaddingValues(
//                        top = if (isLandscape) 0.dp else 80.dp,
//                        start = 20.dp,
//                        end = 20.dp,
////                        bottom = 20.dp
//                        bottom = if (isLandscape) 60.dp else 80.dp
//                    ),
//                    horizontalArrangement = Arrangement.spacedBy(10.dp),
//                    verticalArrangement = Arrangement.spacedBy(10.dp),
//                    modifier = Modifier
//                        .fillMaxSize()
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
////            androidx.compose.material.Text(
////                text = "z rooms",
////                fontSize = 16.sp,
////                fontWeight = FontWeight.Bold,
////                color = Color(0xFFB3B3B3),
////                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
////            )
//            Column(
//                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomLabelPadding),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(
//                    text = "z rooms",
//                    fontSize = 16.sp,
//                    fontWeight = FontWeight.Bold,
//                    color = Color(0xFFB3B3B3)
//                )
//                Spacer(modifier = Modifier.height(4.dp)) // Use Spacer for 4.dp gap
//                if (selectedItem == null) {
//                    Text(
//                        text = buildAnnotatedString {
//                            append("swipe ")
//                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
//                                append("up")
//                            }
//                            append(" from any screen for ")
//                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
//                                append("waking rooms")
//                            }
//                        },
//                        fontSize = 12.sp,
//                        color = Color(0xFFB3B3B3)
//                    )
//                }
//            }
//
//            if (selectedItem == null) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(if (isLandscape) 100.dp else 200.dp) // Reduce height in landscape
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
//
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
//
