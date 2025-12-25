package com.jmisabella.zrooms

import android.content.Context
import android.widget.NumberPicker
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import android.text.format.DateFormat
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import kotlin.math.abs

private fun updateDurationToRemaining(
    context: Context,
    durationMinutes: MutableState<Double>,
    isAlarmEnabled: MutableState<Boolean>
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (prefs.contains("lastWakeTime")) {
        val wakeTime = prefs.getLong("lastWakeTime", 0L)
        val now = System.currentTimeMillis()
        val remainingMillis = wakeTime - now
        if (remainingMillis > 0) {
            durationMinutes.value = min(1440.0, max(0.0, remainingMillis / 60000.0))
        } else {
            durationMinutes.value = 0.0
            isAlarmEnabled.value = false
            prefs.edit().putBoolean("isAlarmEnabled", false).apply()
        }
        prefs.edit().putFloat("durationMinutes", durationMinutes.value.toFloat()).apply()
    }
}

@Composable
fun ExpandingView(
    color: Color,
    dismiss: () -> Unit,
    durationMinutes: MutableState<Double>,
    isAlarmActive: MutableState<Boolean>,
    isAlarmEnabled: MutableState<Boolean>,
    changeRoom: (Int) -> Boolean,
    currentIndex: Int,
    maxIndex: Int,
    selectAlarm: () -> Unit,
    audioService: AudioService? = null
) {
    val context = LocalContext.current

    // Adaptive slider colors based on background brightness
    val sliderColor = if (isLight(color)) {
        Color(0xFF3A3A3A) // Dark grey for bright backgrounds
    } else {
        Color(0xFFE0E0E0) // Light grey for dark backgrounds
    }

    // Custom meditation manager (must be created first)
    val meditationManager = remember { CustomMeditationManager(context) }

    // Text-to-speech manager (needs meditationManager for random meditation selection)
    val ttsManager = remember { TextToSpeechManager(context, meditationManager) }
    val voiceManager = remember { VoiceManager.getInstance(context) }
    var isMeditationPlaying by remember { mutableStateOf(false) }
    var showMeditationList by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }

    // Meditation text display settings
    val showMeditationText = remember {
        mutableStateOf(
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean("showMeditationText", true)
        )
    }

    // Listen for changes to the preference
    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // Check every 500ms for preference changes
            showMeditationText.value = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("showMeditationText", true)
        }
    }

    // Update on room entry
    LaunchedEffect(Unit) {
        if (isAlarmEnabled.value) {
            updateDurationToRemaining(context, durationMinutes, isAlarmEnabled)
        }
    }

    // Periodic update every 60 seconds while enabled
    LaunchedEffect(isAlarmEnabled.value) {
        while (isAlarmEnabled.value) {
            updateDurationToRemaining(context, durationMinutes, isAlarmEnabled)
            delay(60000L)
        }
    }

    // Set up TTS manager and clean up on dispose
    DisposableEffect(Unit) {
        // Set up ambient volume callback
        ttsManager.onAmbientVolumeChanged = { volume ->
            audioService?.setAmbientVolume(volume)
        }
        // Set initial ambient volume
        audioService?.setAmbientVolume(ttsManager.ambientVolume)

        onDispose {
            ttsManager.stopSpeaking()
            ttsManager.shutdown()
        }
    }

    // Track meditation playing state
    LaunchedEffect(ttsManager.isPlayingMeditation) {
        isMeditationPlaying = ttsManager.isPlayingMeditation
    }

    // Ambient volume state (0.0 to 0.6)
    val ambientVolumeState = remember { mutableStateOf(ttsManager.ambientVolume.toDouble()) }

    // Update TTS manager and ambient volume when slider changes
    LaunchedEffect(ambientVolumeState.value) {
        ttsManager.updateAmbientVolume(ambientVolumeState.value.toFloat())
    }

    val defaultDimDurationSeconds = 600.0 // 10 minutes for room entry

    var showLabel by remember { mutableStateOf(false) }
    var showBalanceLabel by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAlarmStateLabel by remember { mutableStateOf(false) }
    var tempWakeTime by remember {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val now = Date()
        val savedTime = prefs.getLong("lastWakeTime", 0L)
        if (savedTime != 0L) {
            mutableStateOf(Date(savedTime))
        } else if (durationMinutes.value == 0.0) {
            val calendar = Calendar.getInstance()
            calendar.time = now
            calendar.add(Calendar.HOUR_OF_DAY, 8)
            mutableStateOf(calendar.time)
        } else {
            mutableStateOf(Date(now.time + (durationMinutes.value * 60 * 1000).toLong()))
        }
    }
    var dimMode by remember { mutableStateOf<DimMode>(DimMode.Duration(defaultDimDurationSeconds)) }
    var roomChangeTrigger by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

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

    var targetBackgroundColor by remember { mutableStateOf(color) }
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "backgroundColor"
    )

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

    val effectiveDimOpacity = if (isAlarmAnimating && isAlarmActive.value) alarmDimOpacity else animatedDimOpacity
    var sunTrigger by remember { mutableIntStateOf(0) }
    var nightsTrigger by remember { mutableIntStateOf(0) }

    val alarmButtonBackground by animateColorAsState(
        targetValue = if (isAlarmEnabled.value) Color(0xFF4CAF50) else Color(0xFF616161),
        animationSpec = tween(durationMillis = 300),
        label = "alarmButtonBackground"
    )

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    LaunchedEffect(durationMinutes.value) {
        if (durationMinutes.value > 0) {
            val now = Date()
            tempWakeTime = Date(now.time + (durationMinutes.value * 60 * 1000).toLong())
            prefs.edit()
                .putLong("lastWakeTime", tempWakeTime.time)
                .putFloat("durationMinutes", durationMinutes.value.toFloat())
                .apply()
        }
    }

    LaunchedEffect(color) {
        targetBackgroundColor = color
    }

    // Initial dimming on room entry
    LaunchedEffect(Unit) {
        if (dimMode is DimMode.Duration) {
            println("Room entered, starting initial dim animation over ${dimSeconds}s")
            currentDimSpec = snap()
            dimTarget = 0f
            dimKey += 1
            delay(1)
            currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
            dimTarget = 1f
            dimKey += 1
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 50.dp.toPx() }

    // Dictionary to map room indices (30-34) to custom titles
    val customRoomTitles = mapOf(
        30 to "Satie: Trois Gymnopédies: No. 1, Lent et douloureux",
        31 to "Bach: Two-Part Invention No. 6 in E Major, BWV 777",
        32 to "Chopin: Prelude No. 2 in A minor, Op. 28, Lento",
//        33 to "Ravel: Piano Concerto in G Major, M. 83 – II. Adagio assai",
        33 to "Bach: Goldberg Variation 15, BWV 988",
        34 to "Schubert: Sonata No. 6 in E minor, II. Allegretto (excerpt)"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            BreathingBackground(color = animatedBackgroundColor)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedDimOpacity))
            )
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            println("ExpandingView swipe area drag started at offset: $offset")
                            dragOffset = Offset.Zero
                        },
                        onDragEnd = {
                            println("ExpandingView swipe area drag ended, dragOffset=$dragOffset")
                            val dx = dragOffset.x
                            val dy = dragOffset.y
                            if (abs(dx) > abs(dy)) {
                                val direction = if (dx > swipeThresholdPx) -1 else if (dx < -swipeThresholdPx) 1 else 0
                                if (direction != 0 && changeRoom(direction)) {
                                    roomChangeTrigger++
                                }
                            } else {
                                if (dy < -swipeThresholdPx) {
                                    println("Swipe up detected in ExpandingView swipe area")
                                    selectAlarm()
                                } else if (dy > swipeThresholdPx) {
                                    println("Swipe down detected in ExpandingView swipe area")
                                    dismiss()
                                }
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
                .pointerInput(Unit) {  // <-- Add this new pointerInput for taps
                    detectTapGestures { offset ->
                        println("Tap detected in ExpandingView at offset: $offset")
                        if (isAlarmActive.value) {
                            // Stop alarm without exiting the room
                            audioService?.stopAll()  // Or audioService?.stopAlarm() if you implement a specific method
                            isAlarmActive.value = false
                        } else {
                            // Exit the room
                            dismiss()
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Column(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        thumbColor = sliderColor,
                        activeTrackColor = sliderColor,
                        inactiveTrackColor = sliderColor.copy(alpha = 0.3f)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Ambient volume slider (0.0 = silent, 1.0 = full)
                    CustomSlider(
                        value = ambientVolumeState,
                        minValue = 0.0,
                        maxValue = 1.0,
                        step = 0.01,
                        onEditingChanged = { editing ->
                            showBalanceLabel = editing
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        thumbColor = sliderColor,
                        activeTrackColor = sliderColor,
                        inactiveTrackColor = sliderColor.copy(alpha = 0.3f)
                    )
                }

                // Overlay labels with fixed positioning
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                ) {
                    if (showLabel) {
                        Text(
                            text = formatDuration(durationMinutes.value),
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (showBalanceLabel) {
                        val ambientPercent = ((ambientVolumeState.value / 1.0) * 100).toInt()
                        Text(
                            text = "ambient $ambientPercent%",
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(8.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showAlarmStateLabel) {
                    Text(
                        text = if (isAlarmEnabled.value) "Alarm Enabled" else "Alarm Disabled",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                Text(
                    text = customRoomTitles[currentIndex] ?: "room ${currentIndex + 1}",
                    color = if (isLight(color)) Color.Black else Color.White,
                    fontSize = if (customRoomTitles.containsKey(currentIndex)) 12.sp else 16.sp,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                        .zIndex(10f), // Ensure buttons are always on top of captions
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Settings Button (leftmost position)
                    Box(
                        modifier = Modifier
                            .clickable {
                                showVoiceSettings = true
                            }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Voice Settings",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.width(40.dp))

                    // Custom Meditations Button
                    Box(
                        modifier = Modifier
                            .clickable {
                                showMeditationList = true
                            }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.FormatQuote,
                            contentDescription = "Custom Meditations",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.width(40.dp))

                    Box(
                        modifier = Modifier
                            .clickable {
                                showTimePicker = true
                            }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = "Set Timer",
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.width(40.dp))

                    Box(
                        modifier = Modifier
                            .clickable {
                                if (isMeditationPlaying) {
                                    ttsManager.stopSpeaking()
                                } else {
                                    ttsManager.startSpeakingRandomMeditation()
                                }
                            }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Eco,
                            contentDescription = if (isMeditationPlaying) "Stop Meditation" else "Play Meditation",
                            tint = if (isMeditationPlaying) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                            modifier = Modifier.size(28.dp)
                        )
                    }

//                    Spacer(Modifier.width(40.dp))
//
//                    Box(
//                        modifier = Modifier
//                            .clickable {
//                                isAlarmEnabled.value = !isAlarmEnabled.value
//                                showAlarmStateLabel = true
//                                PreferenceManager.getDefaultSharedPreferences(context)
//                                    .edit()
//                                    .putBoolean("isAlarmEnabled", isAlarmEnabled.value)
//                                    .apply()
//                                if (isAlarmEnabled.value && durationMinutes.value == 0.0) {
//                                    showTimePicker = true
//                                }
//                            }
//                            .background(alarmButtonBackground, CircleShape)
//                            .padding(12.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            if (isAlarmEnabled.value) Icons.Filled.AlarmOn else Icons.Filled.Alarm,
//                            contentDescription = if (isAlarmEnabled.value) "Alarm Enabled" else "Alarm Disabled",
//                            tint = if (isAlarmEnabled.value) Color.White else Color(0xFFB3B3B3),
//                            modifier = Modifier.size(28.dp)
//                        )
//                    }
                }
            }
        }

        // Meditation text display at bottom (with click-through enabled)
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

        MeditationTextDisplay(
            currentPhrase = ttsManager.currentPhrase,
            previousPhrase = ttsManager.previousPhrase,
            isVisible = showMeditationText.value && isMeditationPlaying,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = if (isPortrait) 48.dp else 0.dp)
        )
    }

    // Hide alarm state label after 2 seconds
    LaunchedEffect(showAlarmStateLabel) {
        if (showAlarmStateLabel) {
            delay(2000)
            showAlarmStateLabel = false
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF212121)) // Dark background
                    .width(300.dp) // Slightly increased width
                    .padding(16.dp), // Maintained padding
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val calendar = Calendar.getInstance()
                calendar.time = tempWakeTime
                val is24Hour = DateFormat.is24HourFormat(context)
                var hour by remember { mutableIntStateOf(if (is24Hour) calendar.get(Calendar.HOUR_OF_DAY) else calendar.get(Calendar.HOUR)) }
                var minute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }
                var amPm by remember { mutableIntStateOf(if (calendar.get(Calendar.AM_PM) == Calendar.AM) 0 else 1) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp), // Maintained height
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AndroidView(
                        factory = { ctx ->
                            NumberPicker(ctx).apply {
                                minValue = if (is24Hour) 0 else 1
                                maxValue = if (is24Hour) 23 else 12
                                value = hour
                                setOnValueChangedListener { _, _, newVal -> hour = newVal }
                                setTextColor(Color.White.toArgb()) // White text for contrast
                                textSize = 28f // Increased text size
                            }
                        },
                        modifier = Modifier.width(80.dp) // Slightly increased width
                    )

                    Text(
                        text = ":",
                        fontSize = 24.sp, // Increased colon size for balance
                        color = Color.White, // White for contrast
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    AndroidView(
                        factory = { ctx ->
                            NumberPicker(ctx).apply {
                                minValue = 0
                                maxValue = 59
                                value = minute
                                setFormatter { value -> String.format("%02d", value) }
                                setOnValueChangedListener { _, _, newVal -> minute = newVal }
                                setTextColor(Color.White.toArgb()) // White text for contrast
                                textSize = 28f // Increased text size
                            }
                        },
                        modifier = Modifier.width(80.dp) // Slightly increased width
                    )

                    if (!is24Hour) {
                        Spacer(Modifier.width(12.dp))
                        AndroidView(
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 0
                                    maxValue = 1
                                    displayedValues = arrayOf("AM", "PM")
                                    value = amPm
                                    setOnValueChangedListener { _, _, newVal -> amPm = newVal }
                                    setTextColor(Color.White.toArgb()) // White text for contrast
                                    textSize = 28f // Increased text size
                                }
                            },
                            modifier = Modifier.width(90.dp) // Slightly increased width
                        )
                    }
                }

                androidx.compose.material.Button(
                    onClick = {
                        val now = Date()
                        val calendar = Calendar.getInstance()
                        var selectedHour = hour
                        if (!is24Hour) {
                            if (amPm == 1 && selectedHour < 12) selectedHour += 12
                            if (amPm == 0 && selectedHour == 12) selectedHour = 0
                        }
                        var wakeDate = calendar.apply {
                            time = now
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, minute)
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

                        tempWakeTime = wakeDate

                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit()
                            .putLong("lastWakeTime", wakeDate.time)
                            .putFloat("durationMinutes", durationMinutes.value.toFloat())
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
                    },
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50), // Green button for consistency
                        contentColor = Color.White
                    )
                ) {
                    Text("Set", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }

    // Custom meditation list dialog
    if (showMeditationList) {
        CustomMeditationListView(
            manager = meditationManager,
            onDismiss = { showMeditationList = false },
            onPlay = { meditationText ->
                ttsManager.startSpeakingWithPauses(meditationText)
            }
        )
    }

    // Voice Settings Dialog
    if (showVoiceSettings) {
        VoiceSettingsView(
            voiceManager = voiceManager,
            onDismiss = {
                showVoiceSettings = false
                // Refresh voice settings when user closes the dialog
                ttsManager.refreshVoiceSettings()
            }
        )
    }

    LaunchedEffect(sunTrigger) {
        if (sunTrigger > 0) {
            println("Sun button clicked, sunTrigger=$sunTrigger, dimTarget=$dimTarget")
            currentFlashSpec = snap()
            flashTarget = 0.8f
            currentDimSpec = snap()
            dimTarget = 0f
            flashKey += 1
            dimKey += 1
            delay(1)
            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
            flashTarget = 0f
            flashKey += 1
        }
    }

    LaunchedEffect(nightsTrigger) {
        if (nightsTrigger > 0) {
            println("Moon button clicked, nightsTrigger=$nightsTrigger, dimTarget=$dimTarget")
            currentDimSpec = snap()
            dimTarget = 0f
            dimKey += 1
            delay(1)
            currentDimSpec = tween(durationMillis = 3000, easing = LinearEasing)
            dimTarget = 1f
            dimKey += 1
        }
    }

    LaunchedEffect(roomChangeTrigger) {
        if (roomChangeTrigger > 0) {
            println("Room change triggered, roomChangeTrigger=$roomChangeTrigger")
            currentFlashSpec = snap()
            flashTarget = 0.8f
            flashKey += 1
            delay(1)
            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
            flashTarget = 0f
            flashKey += 1
            if (dimMode !is DimMode.Bright && dimMode !is DimMode.Dark) {
                currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
                dimTarget = 1f
                dimKey += 1
            }
        }
    }

    LaunchedEffect(isAlarmActive.value) {
        if (isAlarmActive.value) {
            println("Alarm active, starting alarm animation")
            preAlarmDimOpacity = animatedDimOpacity
            isAlarmAnimating = true
        } else {
            println("Alarm inactive, stopping alarm animation")
            isAlarmAnimating = false
            if (dimMode !is DimMode.Bright && dimMode !is DimMode.Dark) {
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
}

fun formatDuration(minutes: Double): String {
    if (minutes == 0.0) return "Infinite"
    val hours = (minutes / 60).toInt()
    val mins = (minutes % 60).toInt()
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

fun isLight(color: Color): Boolean {
    val luminance = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
    return luminance > 0.5f
}

//
//package com.jmisabella.zrooms
//
//import android.content.Context
//import android.widget.NumberPicker
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
//import androidx.compose.foundation.clickable
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
//import androidx.compose.material.Icon
//import androidx.compose.material.Text
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.WbSunny
//import androidx.compose.material.icons.filled.Alarm
//import androidx.compose.material.icons.filled.AlarmOn
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
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.compose.ui.window.Dialog
//import androidx.preference.PreferenceManager
//import java.util.Calendar
//import java.util.Date
//import kotlin.math.max
//import kotlin.math.min
//import kotlinx.coroutines.delay
//import android.text.format.DateFormat
//import androidx.compose.foundation.layout.size
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.draw.alpha
//import androidx.compose.ui.graphics.toArgb
//import kotlin.math.abs
//
//private fun updateDurationToRemaining(
//    context: Context,
//    durationMinutes: MutableState<Double>,
//    isAlarmEnabled: MutableState<Boolean>
//) {
//    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
//    if (prefs.contains("lastWakeTime")) {
//        val wakeTime = prefs.getLong("lastWakeTime", 0L)
//        val now = System.currentTimeMillis()
//        val remainingMillis = wakeTime - now
//        if (remainingMillis > 0) {
//            durationMinutes.value = min(1440.0, max(0.0, remainingMillis / 60000.0))
//        } else {
//            durationMinutes.value = 0.0
//            isAlarmEnabled.value = false
//            prefs.edit().putBoolean("isAlarmEnabled", false).apply()
//        }
//        prefs.edit().putFloat("durationMinutes", durationMinutes.value.toFloat()).apply()
//    }
//}
//
//@Composable
//fun ExpandingView(
//    color: Color,
//    dismiss: () -> Unit,
//    durationMinutes: MutableState<Double>,
//    isAlarmActive: MutableState<Boolean>,
//    isAlarmEnabled: MutableState<Boolean>,
//    changeRoom: (Int) -> Boolean,
//    currentIndex: Int,
//    maxIndex: Int,
//    selectAlarm: () -> Unit,
//    audioService: AudioService? = null
//) {
//    val context = LocalContext.current
//    // Update on room entry
//    LaunchedEffect(Unit) {
//        if (isAlarmEnabled.value) {
//            updateDurationToRemaining(context, durationMinutes, isAlarmEnabled)
//        }
//    }
//
//    // Periodic update every 60 seconds while enabled
//    LaunchedEffect(isAlarmEnabled.value) {
//        while (isAlarmEnabled.value) {
//            updateDurationToRemaining(context, durationMinutes, isAlarmEnabled)
//            delay(60000L)
//        }
//    }
//
//    val defaultDimDurationSeconds = 600.0 // 10 minutes for room entry
//
//    var showLabel by remember { mutableStateOf(false) }
//    var showTimePicker by remember { mutableStateOf(false) }
//    var showAlarmStateLabel by remember { mutableStateOf(false) }
//    var tempWakeTime by remember {
//        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
//        val now = Date()
//        val savedTime = prefs.getLong("lastWakeTime", 0L)
//        if (savedTime != 0L) {
//            mutableStateOf(Date(savedTime))
//        } else if (durationMinutes.value == 0.0) {
//            val calendar = Calendar.getInstance()
//            calendar.time = now
//            calendar.add(Calendar.HOUR_OF_DAY, 8)
//            mutableStateOf(calendar.time)
//        } else {
//            mutableStateOf(Date(now.time + (durationMinutes.value * 60 * 1000).toLong()))
//        }
//    }
//    var dimMode by remember { mutableStateOf<DimMode>(DimMode.Duration(defaultDimDurationSeconds)) }
//    var roomChangeTrigger by remember { mutableIntStateOf(0) }
//    var dragOffset by remember { mutableStateOf(Offset.Zero) }
//
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
//    var targetBackgroundColor by remember { mutableStateOf(color) }
//    val animatedBackgroundColor by animateColorAsState(
//        targetValue = targetBackgroundColor,
//        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
//        label = "backgroundColor"
//    )
//
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
//    val effectiveDimOpacity = if (isAlarmAnimating && isAlarmActive.value) alarmDimOpacity else animatedDimOpacity
//    var sunTrigger by remember { mutableIntStateOf(0) }
//    var nightsTrigger by remember { mutableIntStateOf(0) }
//
//    val alarmButtonBackground by animateColorAsState(
//        targetValue = if (isAlarmEnabled.value) Color(0xFF4CAF50) else Color(0xFF616161),
//        animationSpec = tween(durationMillis = 300),
//        label = "alarmButtonBackground"
//    )
//
//    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
//
//    LaunchedEffect(durationMinutes.value) {
//        if (durationMinutes.value > 0) {
//            val now = Date()
//            tempWakeTime = Date(now.time + (durationMinutes.value * 60 * 1000).toLong())
//            prefs.edit()
//                .putLong("lastWakeTime", tempWakeTime.time)
//                .putFloat("durationMinutes", durationMinutes.value.toFloat())
//                .apply()
//        }
//    }
//
//    LaunchedEffect(color) {
//        targetBackgroundColor = color
//    }
//
//    // Initial dimming on room entry
//    LaunchedEffect(Unit) {
//        if (dimMode is DimMode.Duration) {
//            println("Room entered, starting initial dim animation over ${dimSeconds}s")
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
//    val density = LocalDensity.current
//    val swipeThresholdPx = with(density) { 50.dp.toPx() }
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//    ) {
//        Box(Modifier.fillMaxSize()) {
//            BreathingBackground(color = animatedBackgroundColor)
//            Box(
//                Modifier
//                    .fillMaxSize()
//                    .background(Color.Black.copy(alpha = animatedDimOpacity))
//            )
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
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .windowInsetsPadding(WindowInsets.statusBars)
//                .windowInsetsPadding(WindowInsets.navigationBars)
//                .pointerInput(Unit) {
//                    detectDragGestures(
//                        onDragStart = { offset ->
//                            println("ExpandingView swipe area drag started at offset: $offset")
//                            dragOffset = Offset.Zero
//                        },
//                        onDragEnd = {
//                            println("ExpandingView swipe area drag ended, dragOffset=$dragOffset")
//                            val dx = dragOffset.x
//                            val dy = dragOffset.y
//                            if (abs(dx) > abs(dy)) {
//                                val direction = if (dx > swipeThresholdPx) -1 else if (dx < -swipeThresholdPx) 1 else 0
//                                if (direction != 0 && changeRoom(direction)) {
//                                    roomChangeTrigger++
//                                }
//                            } else {
//                                if (dy < -swipeThresholdPx) {
//                                    println("Swipe up detected in ExpandingView swipe area")
//                                    selectAlarm()
//                                } else if (dy > swipeThresholdPx) {
//                                    println("Swipe down detected in ExpandingView swipe area")
//                                    dismiss()
//                                }
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
//                .pointerInput(Unit) {  // <-- Add this new pointerInput for taps
//                    detectTapGestures { offset ->
//                        println("Tap detected in ExpandingView at offset: $offset")
//                        if (isAlarmActive.value) {
//                            // Stop alarm without exiting the room
//                            audioService?.stopAll()  // Or audioService?.stopAlarm() if you implement a specific method
//                            isAlarmActive.value = false
//                        } else {
//                            // Exit the room
//                            dismiss()
//                        }
//                    }
//                },
//            verticalArrangement = Arrangement.SpaceBetween,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier.padding(top = 40.dp)
//            ) {
//                CustomSlider(
//                    value = durationMinutes,
//                    minValue = 0.0,
//                    maxValue = 1440.0,
//                    step = 1.0,
//                    onEditingChanged = { editing ->
//                        showLabel = editing
//                    },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 20.dp)
//                )
//                if (showLabel) {
//                    Spacer(Modifier.height(8.dp))
//                    Text(
//                        text = formatDuration(durationMinutes.value),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        modifier = Modifier
//                            .background(Color.Black.copy(alpha = 0.5f))
//                            .padding(8.dp)
//                    )
//                }
//            }
//
//            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                if (showAlarmStateLabel) {
//                    Text(
//                        text = if (isAlarmEnabled.value) "Alarm Enabled" else "Alarm Disabled",
//                        color = Color.White,
//                        fontSize = 16.sp,
//                        modifier = Modifier
//                            .background(Color.Black.copy(alpha = 0.7f))
//                            .padding(8.dp)
//                            .align(Alignment.CenterHorizontally)
//                    )
//                }
//
//                Text(
//                    text = "room ${currentIndex + 1}",
//                    color = if (isLight(color)) Color.Black else Color.White,
//                    fontSize = 16.sp,
//                    modifier = Modifier
//                        .padding(bottom = 8.dp)
//                )
//
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(bottom = 40.dp),
//                    horizontalArrangement = Arrangement.Center,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .clickable {
//                                sunTrigger++
//                                dimMode = DimMode.Bright
//                            }
//                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
//                            .padding(12.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            Icons.Filled.WbSunny,
//                            contentDescription = "Brighten Screen",
//                            tint = Color(0xFFFFCA28),
//                            modifier = Modifier.size(28.dp)
//                        )
//                    }
//
//                    Spacer(Modifier.width(40.dp))
//
//                    Box(
//                        modifier = Modifier
//                            .clickable {
//                                nightsTrigger++
//                                dimMode = DimMode.Dark
//                                dimSeconds = 3.0 // Override for moon button
//                            }
//                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
//                            .padding(12.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            Icons.Outlined.NightsStay,
//                            contentDescription = "Dim Screen",
//                            tint = Color(0xFF64B5F6),
//                            modifier = Modifier.size(28.dp)
//                        )
//                    }
//
//                    Spacer(Modifier.width(40.dp))
//
//                    Box(
//                        modifier = Modifier
//                            .clickable(enabled = durationMinutes.value != 0.0 || isAlarmEnabled.value) {
//                                showTimePicker = true
//                            }
//                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
//                            .padding(12.dp)
//                            .alpha(if (durationMinutes.value == 0.0 && !isAlarmEnabled.value) 0.5f else 1f),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            Icons.Outlined.Schedule,
//                            contentDescription = "Set Timer",
//                            tint = Color(0xFFFFA726),
//                            modifier = Modifier.size(28.dp)
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    // Hide alarm state label after 2 seconds
//    LaunchedEffect(showAlarmStateLabel) {
//        if (showAlarmStateLabel) {
//            delay(2000)
//            showAlarmStateLabel = false
//        }
//    }
//
//    if (showTimePicker) {
//        Dialog(onDismissRequest = { showTimePicker = false }) {
//            Column(
//                modifier = Modifier
//                    .background(Color(0xFF212121)) // Dark background
//                    .width(300.dp) // Slightly increased width
//                    .padding(16.dp), // Maintained padding
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                val calendar = Calendar.getInstance()
//                calendar.time = tempWakeTime
//                val is24Hour = DateFormat.is24HourFormat(context)
//                var hour by remember { mutableIntStateOf(if (is24Hour) calendar.get(Calendar.HOUR_OF_DAY) else calendar.get(Calendar.HOUR)) }
//                var minute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }
//                var amPm by remember { mutableIntStateOf(if (calendar.get(Calendar.AM_PM) == Calendar.AM) 0 else 1) }
//
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(120.dp), // Maintained height
//                    horizontalArrangement = Arrangement.Center,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    AndroidView(
//                        factory = { ctx ->
//                            NumberPicker(ctx).apply {
//                                minValue = if (is24Hour) 0 else 1
//                                maxValue = if (is24Hour) 23 else 12
//                                value = hour
//                                setOnValueChangedListener { _, _, newVal -> hour = newVal }
//                                setTextColor(Color.White.toArgb()) // White text for contrast
//                                textSize = 28f // Increased text size
//                            }
//                        },
//                        modifier = Modifier.width(80.dp) // Slightly increased width
//                    )
//
//                    Text(
//                        text = ":",
//                        fontSize = 24.sp, // Increased colon size for balance
//                        color = Color.White, // White for contrast
//                        modifier = Modifier.padding(horizontal = 8.dp)
//                    )
//
//                    AndroidView(
//                        factory = { ctx ->
//                            NumberPicker(ctx).apply {
//                                minValue = 0
//                                maxValue = 59
//                                value = minute
//                                setFormatter { value -> String.format("%02d", value) }
//                                setOnValueChangedListener { _, _, newVal -> minute = newVal }
//                                setTextColor(Color.White.toArgb()) // White text for contrast
//                                textSize = 28f // Increased text size
//                            }
//                        },
//                        modifier = Modifier.width(80.dp) // Slightly increased width
//                    )
//
//                    if (!is24Hour) {
//                        Spacer(Modifier.width(12.dp))
//                        AndroidView(
//                            factory = { ctx ->
//                                NumberPicker(ctx).apply {
//                                    minValue = 0
//                                    maxValue = 1
//                                    displayedValues = arrayOf("AM", "PM")
//                                    value = amPm
//                                    setOnValueChangedListener { _, _, newVal -> amPm = newVal }
//                                    setTextColor(Color.White.toArgb()) // White text for contrast
//                                    textSize = 28f // Increased text size
//                                }
//                            },
//                            modifier = Modifier.width(90.dp) // Slightly increased width
//                        )
//                    }
//                }
//
//                androidx.compose.material.Button(
//                    onClick = {
//                        val now = Date()
//                        val calendar = Calendar.getInstance()
//                        var selectedHour = hour
//                        if (!is24Hour) {
//                            if (amPm == 1 && selectedHour < 12) selectedHour += 12
//                            if (amPm == 0 && selectedHour == 12) selectedHour = 0
//                        }
//                        var wakeDate = calendar.apply {
//                            time = now
//                            set(Calendar.HOUR_OF_DAY, selectedHour)
//                            set(Calendar.MINUTE, minute)
//                            set(Calendar.SECOND, 0)
//                        }.time
//
//                        if (wakeDate <= now) {
//                            wakeDate = Calendar.getInstance().apply {
//                                time = wakeDate
//                                add(Calendar.DAY_OF_MONTH, 1)
//                            }.time
//                        }
//
//                        val durationSeconds = (wakeDate.time - now.time) / 1000.0
//                        durationMinutes.value = max(1.0, min(1440.0, durationSeconds / 60))
//
//                        tempWakeTime = wakeDate
//
//                        PreferenceManager.getDefaultSharedPreferences(context)
//                            .edit()
//                            .putLong("lastWakeTime", wakeDate.time)
//                            .putFloat("durationMinutes", durationMinutes.value.toFloat())
//                            .apply()
//
//                        if (!isAlarmEnabled.value) {
//                            isAlarmEnabled.value = true
//                            PreferenceManager.getDefaultSharedPreferences(context)
//                                .edit()
//                                .putBoolean("isAlarmEnabled", true)
//                                .apply()
//                            selectAlarm()
//                        }
//
//                        showTimePicker = false
//                    },
//                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
//                        backgroundColor = Color(0xFF4CAF50), // Green button for consistency
//                        contentColor = Color.White
//                    )
//                ) {
//                    Text("Set", color = Color.White, fontSize = 16.sp)
//                }
//            }
//        }
//    }
//
//    LaunchedEffect(sunTrigger) {
//        if (sunTrigger > 0) {
//            println("Sun button clicked, sunTrigger=$sunTrigger, dimTarget=$dimTarget")
//            currentFlashSpec = snap()
//            flashTarget = 0.8f
//            currentDimSpec = snap()
//            dimTarget = 0f
//            flashKey += 1
//            dimKey += 1
//            delay(1)
//            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
//            flashTarget = 0f
//            flashKey += 1
//        }
//    }
//
//    LaunchedEffect(nightsTrigger) {
//        if (nightsTrigger > 0) {
//            println("Moon button clicked, nightsTrigger=$nightsTrigger, dimTarget=$dimTarget")
//            currentDimSpec = snap()
//            dimTarget = 0f
//            dimKey += 1
//            delay(1)
//            currentDimSpec = tween(durationMillis = 3000, easing = LinearEasing)
//            dimTarget = 1f
//            dimKey += 1
//        }
//    }
//
//    LaunchedEffect(roomChangeTrigger) {
//        if (roomChangeTrigger > 0) {
//            println("Room change triggered, roomChangeTrigger=$roomChangeTrigger")
//            currentFlashSpec = snap()
//            flashTarget = 0.8f
//            flashKey += 1
//            delay(1)
//            currentFlashSpec = tween(durationMillis = 500, easing = LinearEasing)
//            flashTarget = 0f
//            flashKey += 1
//            if (dimMode !is DimMode.Bright && dimMode !is DimMode.Dark) {
//                currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//                dimTarget = 1f
//                dimKey += 1
//            }
//        }
//    }
//
//    LaunchedEffect(isAlarmActive.value) {
//        if (isAlarmActive.value) {
//            println("Alarm active, starting alarm animation")
//            preAlarmDimOpacity = animatedDimOpacity
//            isAlarmAnimating = true
//        } else {
//            println("Alarm inactive, stopping alarm animation")
//            isAlarmAnimating = false
//            if (dimMode !is DimMode.Bright && dimMode !is DimMode.Dark) {
//                currentDimSpec = snap()
//                dimTarget = 0f
//                dimKey += 1
//                delay(1)
//                currentDimSpec = tween(durationMillis = (dimSeconds * 1000).toInt(), easing = LinearEasing)
//                dimTarget = 1f
//                dimKey += 1
//            }
//        }
//    }
//}
//
//fun formatDuration(minutes: Double): String {
//    if (minutes == 0.0) return "Infinite"
//    val hours = (minutes / 60).toInt()
//    val mins = (minutes % 60).toInt()
//    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
//}
//
//fun isLight(color: Color): Boolean {
//    val luminance = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
//    return luminance > 0.5f
//}
