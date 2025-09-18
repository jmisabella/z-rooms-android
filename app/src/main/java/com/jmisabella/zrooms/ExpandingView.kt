package com.jmisabella.zrooms

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.Brightness7
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

@Composable
fun ExpandingView(
    color: Color,
    dismiss: () -> Unit,
    durationMinutes: MutableState<Double>,
    isAlarmActive: MutableState<Boolean>,
    isAlarmEnabled: MutableState<Boolean>,
    changeRoom: (Int) -> Unit,
    currentIndex: Int,
    maxIndex: Int,
    selectAlarm: () -> Unit
) {
    val context = LocalContext.current
    val defaultDimDurationMinutes = 3.0
    val defaultDimDurationSeconds = defaultDimDurationMinutes * 60

    var showLabel by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tempWakeTime by remember { mutableStateOf(Date()) }
    var dimMode by remember { mutableStateOf<DimMode>(DimMode.Duration(defaultDimDurationSeconds)) }
    var roomChangeTrigger by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Animation states
    var dimTarget by remember { mutableFloatStateOf(0f) }
    var dimSeconds by remember { mutableDoubleStateOf(defaultDimDurationSeconds) }
    var dimKey by remember { mutableIntStateOf(0) }
    var currentDimSpec by remember { mutableStateOf<FiniteAnimationSpec<Float>>(snap()) }
    val animatedDimOpacity by animateFloatAsState(dimTarget, currentDimSpec, label = "dim")

    var flashTarget by remember { mutableFloatStateOf(0f) }
    var flashSeconds by remember { mutableDoubleStateOf(0.5) }
    var flashKey by remember { mutableIntStateOf(0) }
    var currentFlashSpec by remember { mutableStateOf<FiniteAnimationSpec<Float>>(snap()) }
    val animatedFlashOpacity by animateFloatAsState(flashTarget, currentFlashSpec, label = "flash")

    // Background color animation
    var targetBackgroundColor by remember { mutableStateOf(color) }
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing),
        label = "backgroundColor"
    )

    // Alarm animation
    val infiniteTransition = rememberInfiniteTransition(label = "alarm")
    var isAlarmAnimating by remember { mutableStateOf(false) }
    var preAlarmDimOpacity by remember { mutableFloatStateOf(animatedDimOpacity) }
    val alarmDimOpacity by infiniteTransition.animateFloat(
        initialValue = preAlarmDimOpacity,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmDim"
    )

    val effectiveDimOpacity = if (isAlarmAnimating) alarmDimOpacity else animatedDimOpacity
    var sunTrigger by remember { mutableIntStateOf(0) }
    var nightsTrigger by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { dismiss() }
            }
    ) {
        // Background and overlays
        Box(Modifier.fillMaxSize()) {
            BreathingBackground(color = animatedBackgroundColor)
            if (isAlarmActive.value) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(hsvToColor(0.58f, 0.3f, 0.9f).copy(alpha = effectiveDimOpacity))
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = animatedFlashOpacity))
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp), // Reserve space for swipe area
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomSlider(
                value = durationMinutes,
                minValue = 0.0,
                maxValue = 1440.0,
                step = 1.0,
                onEditingChanged = { editing -> showLabel = editing },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )

            if (showLabel) {
                val text = when {
                    durationMinutes.value == 0.0 -> "infinite"
                    durationMinutes.value < 60 -> {
                        val minutes = durationMinutes.value.toInt()
                        "$minutes minute${if (minutes == 1) "" else "s"}"
                    }
                    else -> {
                        val hours = (durationMinutes.value / 60).toInt()
                        val minutes = (durationMinutes.value % 60).toInt()
                        if (minutes == 0) {
                            "$hours hour${if (hours == 1) "" else "s"}"
                        } else {
                            "$hours hour${if (hours == 1) "" else "s"}, $minutes minute${if (minutes == 1) "" else "s"}"
                        }
                    }
                }
                Text(
                    text = text,
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragOffset = Offset.Zero
                                println("ExpandingView upper swipe area drag started at offset: $it")
                            },
                            onDragEnd = {
                                println("ExpandingView upper swipe area drag ended, dragOffset=$dragOffset")
                                if (dragOffset.y < -50) { // Swipe up
                                    println("Swipe up detected in ExpandingView upper swipe area")
                                    selectAlarm()
                                } else if (dragOffset.y > 50) { // Swipe down
                                    println("Swipe down detected in ExpandingView upper swipe area")
                                    dismiss()
                                } else if (dragOffset.x < -50) { // Swipe left
                                    changeRoom(1)
                                } else if (dragOffset.x > 50) { // Swipe right
                                    changeRoom(-1)
                                }
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                println("ExpandingView upper swipe area drag cancelled")
                                dragOffset = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                println("ExpandingView upper swipe area drag detected, dragAmount=$dragAmount, dragOffset=$dragOffset")
                            }
                        )
                    }
            )

            Text(
                text = "room ${currentIndex + 1}",
                fontSize = 14.sp,
                color = if (currentIndex + 1 <= 5 || currentIndex + 1 > 25) Color(0xFFB3B3B3) else Color(0xFF4D4D4D),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Button(
                    onClick = {
                        dimMode = DimMode.Duration(defaultDimDurationSeconds)
                        targetBackgroundColor = color
                        sunTrigger += 1
                    },
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Outlined.Brightness7,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                    )
                }

                Button(
                    onClick = {
                        dimMode = DimMode.Duration(3.0)
                        nightsTrigger += 1
                    },
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Outlined.NightsStay,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                    )
                }

                Button(
                    onClick = {
                        if (durationMinutes.value > 0) {
                            isAlarmEnabled.value = !isAlarmEnabled.value
                            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("isAlarmEnabled", isAlarmEnabled.value).apply()
                            if (isAlarmEnabled.value) {
                                selectAlarm()
                            }
                        }
                    },
                    enabled = durationMinutes.value > 0,
                    shape = CircleShape
                ) {
                    Icon(
                        if (isAlarmEnabled.value) Icons.Outlined.AlarmOn else Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                    )
                }

                Button(
                    onClick = {
                        val now = Date()
                        val savedTime = PreferenceManager.getDefaultSharedPreferences(context)
                            .getLong("lastWakeTime", 0L)
                            .let { if (it > 0) Date(it) else null }
                        tempWakeTime = when {
                            savedTime != null -> savedTime
                            durationMinutes.value == 0.0 -> Calendar.getInstance().apply {
                                time = now
                                add(Calendar.HOUR_OF_DAY, 8)
                            }.time
                            else -> Date(now.time + (durationMinutes.value * 60 * 1000).toLong())
                        }
                        showTimePicker = true
                    },
                    enabled = durationMinutes.value > 0 || isAlarmEnabled.value,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                    )
                }
            }
        }

        // Swipe area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = Offset.Zero
                            println("ExpandingView swipe area drag started at offset: $it")
                        },
                        onDragEnd = {
                            println("ExpandingView swipe area drag ended, dragOffset=$dragOffset")
                            if (dragOffset.y < -50) { // Swipe up
                                println("Swipe up detected in ExpandingView swipe area")
                                selectAlarm()
                            } else if (dragOffset.y > 50) { // Swipe down
                                println("Swipe down detected in ExpandingView swipe area")
                                dismiss()
                            } else if (dragOffset.x < -50) { // Swipe left
                                changeRoom(1)
                            } else if (dragOffset.x > 50) { // Swipe right
                                changeRoom(-1)
                            }
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            println("ExpandingView swipe area drag cancelled")
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                            println("ExpandingView swipe area drag detected, dragAmount=$dragAmount, dragOffset=$dragOffset")
                        }
                    )
                }
        )

        if (showTimePicker) {
            Dialog(onDismissRequest = { showTimePicker = false }) {
                Column(
                    modifier = Modifier
                        .background(Color.White)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Select Wake Time (Implement TimePicker here; this is a placeholder)",
                        color = Color.Black,
                        fontSize = 16.sp
                    )
                    Button(
                        onClick = {
                            val now = Date()
                            val calendar = Calendar.getInstance()
                            calendar.time = tempWakeTime
                            val components = calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
                            var wakeDate = calendar.apply {
                                set(Calendar.HOUR_OF_DAY, components.first)
                                set(Calendar.MINUTE, components.second)
                                set(Calendar.SECOND, 0)
                            }.time

                            if (wakeDate <= now) {
                                wakeDate = Calendar.getInstance().apply {
                                    time = wakeDate
                                    add(Calendar.DAY_OF_MONTH, 1)
                                }.time
                            }

                            val durationSeconds = (wakeDate.time - now.time) / 1000.0
                            durationMinutes.value = max(1.0, min(1440.0, durationSeconds / 60))
                            PreferenceManager.getDefaultSharedPreferences(context)
                                .edit()
                                .putLong("lastWakeTime", tempWakeTime.time)
                                .apply()

                            if (!isAlarmEnabled.value) {
                                isAlarmEnabled.value = true
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit()
                                    .putBoolean("isAlarmEnabled", true)
                                    .apply()
                                selectAlarm()
                            }

                            showTimePicker = false
                        }
                    ) {
                        Text("Set", color = Color.White)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (dimMode is DimMode.Duration) {
            currentDimSpec = snap()
            dimTarget = 0f
            dimKey += 1
            delay(1)
            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
            dimTarget = 1f
            dimKey += 1
        }
    }

    LaunchedEffect(isAlarmActive.value) {
        if (isAlarmActive.value) {
            preAlarmDimOpacity = animatedDimOpacity
            isAlarmAnimating = true
        } else {
            isAlarmAnimating = false
            currentDimSpec = snap()
            dimTarget = 0f
            dimKey += 1
            delay(1)
            if (dimMode is DimMode.Duration) {
                currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
                dimTarget = 1f
                dimKey += 1
            }
        }
    }

    LaunchedEffect(roomChangeTrigger) {
        currentFlashSpec = snap()
        flashTarget = 0.8f
        currentDimSpec = snap()
        dimTarget = 0f
        flashKey += 1
        dimKey += 1
        delay(1)
        currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
        flashTarget = 0f
        currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
        dimTarget = 1f
        flashKey += 1
        dimKey += 1
    }

    LaunchedEffect(sunTrigger) {
        if (sunTrigger > 0) {
            currentFlashSpec = snap()
            flashTarget = 0.8f
            currentDimSpec = snap()
            dimTarget = 0f
            flashKey += 1
            dimKey += 1
            delay(1)
            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
            flashTarget = 0f
            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
            dimTarget = 1f
            flashKey += 1
            dimKey += 1
        }
    }

    LaunchedEffect(nightsTrigger) {
        if (nightsTrigger > 0) {
            dimSeconds = 3.0
            targetBackgroundColor = Color.Black
            currentDimSpec = snap()
            dimTarget = 0f
            dimKey += 1
            delay(1)
            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
            dimTarget = 1f
            dimKey += 1
        }
    }
}

//package com.jmisabella.zrooms
//
//import android.content.Context
//import androidx.compose.animation.animateColorAsState
//import androidx.compose.animation.core.FiniteAnimationSpec
//import androidx.compose.animation.core.LinearEasing
//import androidx.compose.animation.core.RepeatMode
//import androidx.compose.animation.core.animateFloat
//import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.core.infiniteRepeatable
//import androidx.compose.animation.core.rememberInfiniteTransition
//import androidx.compose.animation.core.snap
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.gestures.detectDragGestures
//import androidx.compose.foundation.gestures.detectTapGestures
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.layout.WindowInsets
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.navigationBars
//import androidx.compose.foundation.layout.statusBars
//import androidx.compose.foundation.layout.windowInsetsPadding
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.material.Button
//import androidx.compose.material.Icon
//import androidx.compose.material.Text
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.outlined.Alarm
//import androidx.compose.material.icons.outlined.AlarmOn
//import androidx.compose.material.icons.outlined.Brightness7
//import androidx.compose.material.icons.outlined.NightsStay
//import androidx.compose.material.icons.outlined.Schedule
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.MutableState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableDoubleStateOf
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.mutableIntStateOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.window.Dialog
//import androidx.preference.PreferenceManager
//import java.util.Calendar
//import java.util.Date
//import kotlin.math.max
//import kotlin.math.min
//import kotlinx.coroutines.delay
//
//@Composable
//fun ExpandingView(
//    color: Color,
//    dismiss: () -> Unit,
//    durationMinutes: MutableState<Double>,
//    isAlarmActive: MutableState<Boolean>,
//    isAlarmEnabled: MutableState<Boolean>,
//    changeRoom: (Int) -> Unit,
//    currentIndex: Int,
//    maxIndex: Int,
//    selectAlarm: () -> Unit
//) {
//    val context = LocalContext.current
//    val defaultDimDurationMinutes = 3.0
//    val defaultDimDurationSeconds = defaultDimDurationMinutes * 60
//
//    var showLabel by remember { mutableStateOf(false) }
//    var showTimePicker by remember { mutableStateOf(false) }
//    var tempWakeTime by remember { mutableStateOf(Date()) }
//    var dimMode by remember { mutableStateOf<DimMode>(DimMode.Duration(defaultDimDurationSeconds)) }
//    var roomChangeTrigger by remember { mutableIntStateOf(0) }
//    var dragOffset by remember { mutableStateOf(Offset.Zero) }
//
//    // Animation states
//    var dimTarget by remember { mutableFloatStateOf(0f) }
//    var dimSeconds by remember { mutableDoubleStateOf(defaultDimDurationSeconds) }
//    var dimKey by remember { mutableIntStateOf(0) }
//    var currentDimSpec by remember { mutableStateOf<FiniteAnimationSpec<Float>>(snap()) }
//    val animatedDimOpacity by animateFloatAsState(dimTarget, currentDimSpec, label = "dim")
//
//    var flashTarget by remember { mutableFloatStateOf(0f) }
//    var flashSeconds by remember { mutableDoubleStateOf(0.5) }
//    var flashKey by remember { mutableIntStateOf(0) }
//    var currentFlashSpec by remember { mutableStateOf<FiniteAnimationSpec<Float>>(snap()) }
//    val animatedFlashOpacity by animateFloatAsState(flashTarget, currentFlashSpec, label = "flash")
//
//    // Background color animation
//    var targetBackgroundColor by remember { mutableStateOf(color) }
//    val animatedBackgroundColor by animateColorAsState(
//        targetValue = targetBackgroundColor,
//        animationSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing),
//        label = "backgroundColor"
//    )
//
//    // Alarm animation
//    val infiniteTransition = rememberInfiniteTransition(label = "alarm")
//    var isAlarmAnimating by remember { mutableStateOf(false) }
//    var preAlarmDimOpacity by remember { mutableFloatStateOf(animatedDimOpacity) }
//    val alarmDimOpacity by infiniteTransition.animateFloat(
//        initialValue = preAlarmDimOpacity,
//        targetValue = 0.8f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(durationMillis = 1500, easing = LinearEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "alarmDim"
//    )
//
//    val effectiveDimOpacity = if (isAlarmAnimating) alarmDimOpacity else animatedDimOpacity
//    var sunTrigger by remember { mutableIntStateOf(0) }
//    var nightsTrigger by remember { mutableIntStateOf(0) }
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .pointerInput(Unit) {
//                detectTapGestures { dismiss() }
//            }
//    ) {
//        // Background and overlays
//        Box(Modifier.fillMaxSize()) {
//            BreathingBackground(color = animatedBackgroundColor)
//            if (isAlarmActive.value) {
//                Box(
//                    Modifier
//                        .fillMaxSize()
//                        .background(hsvToColor(0.58f, 0.3f, 0.9f).copy(alpha = effectiveDimOpacity))
//                )
//            }
//            Box(
//                Modifier
//                    .fillMaxSize()
//                    .background(Color.White.copy(alpha = animatedFlashOpacity))
//            )
//        }
//
//        // Main content
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(bottom = 100.dp), // Reserve space for swipe area
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            CustomSlider(
//                value = durationMinutes,
//                minValue = 0.0,
//                maxValue = 1440.0,
//                step = 1.0,
//                onEditingChanged = { editing -> showLabel = editing },
//                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
//            )
//
//            if (showLabel) {
//                val text = when {
//                    durationMinutes.value == 0.0 -> "infinite"
//                    durationMinutes.value < 60 -> {
//                        val minutes = durationMinutes.value.toInt()
//                        "$minutes minute${if (minutes == 1) "" else "s"}"
//                    }
//                    else -> {
//                        val hours = (durationMinutes.value / 60).toInt()
//                        val minutes = (durationMinutes.value % 60).toInt()
//                        if (minutes == 0) {
//                            "$hours hour${if (hours == 1) "" else "s"}"
//                        } else {
//                            "$hours hour${if (hours == 1) "" else "s"}, $minutes minute${if (minutes == 1) "" else "s"}"
//                        }
//                    }
//                }
//                Text(
//                    text = text,
//                    fontSize = 24.sp,
//                    color = Color.White,
//                    modifier = Modifier
//                        .background(Color.Black.copy(alpha = 0.5f))
//                        .padding(8.dp)
//                )
//            }
//
//            Box(
//                modifier = Modifier
//                    .weight(1f)
//                    .fillMaxWidth()
//                    .pointerInput(Unit) {
//                        detectDragGestures(
//                            onDragStart = {
//                                dragOffset = Offset.Zero
//                                println("ExpandingView upper swipe area drag started at offset: $it")
//                            },
//                            onDragEnd = {
//                                println("ExpandingView upper swipe area drag ended, dragOffset=$dragOffset")
//                                if (dragOffset.y < -50) { // Swipe up
//                                    println("Swipe up detected in ExpandingView upper swipe area")
//                                    selectAlarm()
//                                } else if (dragOffset.y > 50) { // Swipe down
//                                    println("Swipe down detected in ExpandingView upper swipe area")
//                                    dismiss()
//                                } else if (dragOffset.x < -50) { // Swipe left
//                                    changeRoom(1)
//                                } else if (dragOffset.x > 50) { // Swipe right
//                                    changeRoom(-1)
//                                }
//                                dragOffset = Offset.Zero
//                            },
//                            onDragCancel = {
//                                println("ExpandingView upper swipe area drag cancelled")
//                                dragOffset = Offset.Zero
//                            },
//                            onDrag = { change, dragAmount ->
//                                change.consume()
//                                dragOffset += dragAmount
//                                println("ExpandingView upper swipe area drag detected, dragAmount=$dragAmount, dragOffset=$dragOffset")
//                            }
//                        )
//                    }
//            )
//
//            Text(
//                text = "room ${currentIndex + 1}",
//                fontSize = 14.sp,
//                color = if (currentIndex + 1 <= 5 || currentIndex + 1 > 25) Color(0xFFB3B3B3) else Color(0xFF4D4D4D),
//                modifier = Modifier.padding(bottom = 20.dp)
//            )
//
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(40.dp),
//                modifier = Modifier.padding(bottom = 20.dp)
//            ) {
//                Button(
//                    onClick = {
//                        dimMode = DimMode.Duration(defaultDimDurationSeconds)
//                        targetBackgroundColor = color
//                        sunTrigger += 1
//                    },
//                    shape = CircleShape
//                ) {
//                    Icon(
//                        Icons.Outlined.Brightness7,
//                        contentDescription = null,
//                        tint = Color(0xFFB3B3B3),
//                        modifier = Modifier
//                            .width(34.dp)
//                            .height(34.dp)
//                    )
//                }
//
//                Button(
//                    onClick = {
//                        dimMode = DimMode.Duration(3.0)
//                        nightsTrigger += 1
//                    },
//                    shape = CircleShape
//                ) {
//                    Icon(
//                        Icons.Outlined.NightsStay,
//                        contentDescription = null,
//                        tint = Color(0xFFB3B3B3),
//                        modifier = Modifier
//                            .width(34.dp)
//                            .height(34.dp)
//                    )
//                }
//
//                Button(
//                    onClick = {
//                        if (durationMinutes.value > 0) {
//                            isAlarmEnabled.value = !isAlarmEnabled.value
//                            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("isAlarmEnabled", isAlarmEnabled.value).apply()
//                            if (isAlarmEnabled.value) {
//                                selectAlarm()
//                            }
//                        }
//                    },
//                    enabled = durationMinutes.value > 0,
//                    shape = CircleShape
//                ) {
//                    Icon(
//                        if (isAlarmEnabled.value) Icons.Outlined.AlarmOn else Icons.Outlined.Alarm,
//                        contentDescription = null,
//                        tint = Color(0xFFB3B3B3),
//                        modifier = Modifier
//                            .width(34.dp)
//                            .height(34.dp)
//                    )
//                }
//
//                Button(
//                    onClick = {
//                        val now = Date()
//                        val savedTime = PreferenceManager.getDefaultSharedPreferences(context)
//                            .getLong("lastWakeTime", 0L)
//                            .let { if (it > 0) Date(it) else null }
//                        tempWakeTime = when {
//                            savedTime != null -> savedTime
//                            durationMinutes.value == 0.0 -> Calendar.getInstance().apply {
//                                time = now
//                                add(Calendar.HOUR_OF_DAY, 8)
//                            }.time
//                            else -> Date(now.time + (durationMinutes.value * 60 * 1000).toLong())
//                        }
//                        showTimePicker = true
//                    },
//                    enabled = durationMinutes.value > 0 || isAlarmEnabled.value,
//                    shape = CircleShape
//                ) {
//                    Icon(
//                        Icons.Outlined.Schedule,
//                        contentDescription = null,
//                        tint = Color(0xFFB3B3B3),
//                        modifier = Modifier
//                            .width(34.dp)
//                            .height(34.dp)
//                    )
//                }
//            }
//        }
//
//        // Swipe area
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(100.dp)
//                .align(Alignment.BottomCenter)
//                .windowInsetsPadding(WindowInsets.navigationBars)
//                .background(Color.White.copy(alpha = 0.3f)) // For testing
//                .border(2.dp, Color.Red) // For testing
//                .pointerInput(Unit) {
//                    detectDragGestures(
//                        onDragStart = {
//                            dragOffset = Offset.Zero
//                            println("ExpandingView swipe area drag started at offset: $it")
//                        },
//                        onDragEnd = {
//                            println("ExpandingView swipe area drag ended, dragOffset=$dragOffset")
//                            if (dragOffset.y < -50) { // Swipe up
//                                println("Swipe up detected in ExpandingView swipe area")
//                                selectAlarm()
//                            } else if (dragOffset.y > 50) { // Swipe down
//                                println("Swipe down detected in ExpandingView swipe area")
//                                dismiss()
//                            } else if (dragOffset.x < -50) { // Swipe left
//                                changeRoom(1)
//                            } else if (dragOffset.x > 50) { // Swipe right
//                                changeRoom(-1)
//                            }
//                            dragOffset = Offset.Zero
//                        },
//                        onDragCancel = {
//                            println("ExpandingView swipe area drag cancelled")
//                            dragOffset = Offset.Zero
//                        },
//                        onDrag = { change, dragAmount ->
//                            change.consume()
//                            dragOffset += dragAmount
//                            println("ExpandingView swipe area drag detected, dragAmount=$dragAmount, dragOffset=$dragOffset")
//                        }
//                    )
//                }
//        )
//
//        if (showTimePicker) {
//            Dialog(onDismissRequest = { showTimePicker = false }) {
//                Column(
//                    modifier = Modifier
//                        .background(Color.White)
//                        .padding(20.dp),
//                    verticalArrangement = Arrangement.spacedBy(20.dp)
//                ) {
//                    Text(
//                        text = "Select Wake Time (Implement TimePicker here; this is a placeholder)",
//                        color = Color.Black,
//                        fontSize = 16.sp
//                    )
//                    Button(
//                        onClick = {
//                            val now = Date()
//                            val calendar = Calendar.getInstance()
//                            calendar.time = tempWakeTime
//                            val components = calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
//                            var wakeDate = calendar.apply {
//                                set(Calendar.HOUR_OF_DAY, components.first)
//                                set(Calendar.MINUTE, components.second)
//                                set(Calendar.SECOND, 0)
//                            }.time
//
//                            if (wakeDate <= now) {
//                                wakeDate = Calendar.getInstance().apply {
//                                    time = wakeDate
//                                    add(Calendar.DAY_OF_MONTH, 1)
//                                }.time
//                            }
//
//                            val durationSeconds = (wakeDate.time - now.time) / 1000.0
//                            durationMinutes.value = max(1.0, min(1440.0, durationSeconds / 60))
//                            PreferenceManager.getDefaultSharedPreferences(context)
//                                .edit()
//                                .putLong("lastWakeTime", tempWakeTime.time)
//                                .apply()
//
//                            if (!isAlarmEnabled.value) {
//                                isAlarmEnabled.value = true
//                                PreferenceManager.getDefaultSharedPreferences(context)
//                                    .edit()
//                                    .putBoolean("isAlarmEnabled", true)
//                                    .apply()
//                                selectAlarm()
//                            }
//
//                            showTimePicker = false
//                        }
//                    ) {
//                        Text("Set", color = Color.White)
//                    }
//                }
//            }
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        if (dimMode is DimMode.Duration) {
//            currentDimSpec = snap()
//            dimTarget = 0f
//            dimKey += 1
//            delay(1)
//            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//            dimTarget = 1f
//            dimKey += 1
//        }
//    }
//
//    LaunchedEffect(isAlarmActive.value) {
//        if (isAlarmActive.value) {
//            preAlarmDimOpacity = animatedDimOpacity
//            isAlarmAnimating = true
//        } else {
//            isAlarmAnimating = false
//            currentDimSpec = snap()
//            dimTarget = 0f
//            dimKey += 1
//            delay(1)
//            if (dimMode is DimMode.Duration) {
//                currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//                dimTarget = 1f
//                dimKey += 1
//            }
//        }
//    }
//
//    LaunchedEffect(roomChangeTrigger) {
//        currentFlashSpec = snap()
//        flashTarget = 0.8f
//        currentDimSpec = snap()
//        dimTarget = 0f
//        flashKey += 1
//        dimKey += 1
//        delay(1)
//        currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
//        flashTarget = 0f
//        currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//        dimTarget = 1f
//        flashKey += 1
//        dimKey += 1
//    }
//
//    LaunchedEffect(sunTrigger) {
//        if (sunTrigger > 0) {
//            currentFlashSpec = snap()
//            flashTarget = 0.8f
//            currentDimSpec = snap()
//            dimTarget = 0f
//            flashKey += 1
//            dimKey += 1
//            delay(1)
//            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
//            flashTarget = 0f
//            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//            dimTarget = 1f
//            flashKey += 1
//            dimKey += 1
//        }
//    }
//
//    LaunchedEffect(nightsTrigger) {
//        if (nightsTrigger > 0) {
//            dimSeconds = 3.0
//            targetBackgroundColor = Color.Black
//            currentDimSpec = snap()
//            dimTarget = 0f
//            dimKey += 1
//            delay(1)
//            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//            dimTarget = 1f
//            dimKey += 1
//        }
//    }
//}

