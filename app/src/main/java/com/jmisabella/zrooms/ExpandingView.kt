package com.jmisabella.zrooms

import android.content.Context
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
    var roomChangeTrigger by remember { mutableIntStateOf(0) } // Use int for trigger

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

    // Triggers for buttons
    var sunTrigger by remember { mutableIntStateOf(0) }
    var nightsTrigger by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { dismiss() }
                detectDragGestures { _, dragAmount ->
                    val translationHeight = dragAmount.y
                    val translationWidth = dragAmount.x
                    if (translationHeight < -50) {
                        selectAlarm()
                    } else if (translationHeight > 100) {
                        dismiss()
                    } else if (translationWidth < -50) {
                        changeRoom(1)
                        roomChangeTrigger += 1
                    } else if (translationWidth > 50) {
                        changeRoom(-1)
                        roomChangeTrigger += 1
                    }
                }
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            BreathingBackground(color = color)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isAlarmActive.value) hsvToColor(0.58f, 0.3f, 0.9f).copy(alpha = effectiveDimOpacity)
                        else Color.Black.copy(alpha = effectiveDimOpacity)
                    )
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = animatedFlashOpacity))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomSlider(
                value = durationMinutes,
                minValue = 0.0,
                maxValue = 1440.0,
                step = 1.0,
                onEditingChanged = { editing ->
                    showLabel = editing
                },
                modifier = Modifier.padding(horizontal = 40.dp)
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

            Spacer(Modifier.weight(1f))

            Text(
                text = "room ${currentIndex + 1}",
                fontSize = 14.sp,
                color = if (currentIndex + 1 <= 5 || currentIndex + 1 >= 28) Color(0xFFB3B3B3) else Color(0xFF4D4D4D),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                Button(
                    onClick = {
                        dimMode = DimMode.Duration(defaultDimDurationSeconds)
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
                        dimMode = DimMode.Duration(4.0)
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

    // OnAppear equivalent
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

    // Alarm state change
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

    // Room change trigger
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

    // Sun button sequence
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

    // Nights button sequence
    LaunchedEffect(nightsTrigger) {
        if (nightsTrigger > 0) {
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

//sealed class DimMode {
//    data class Duration(val seconds: Double) : DimMode()
//}
//
//fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
//    return Color.hsl(hue * 360f, saturation, value)
//}