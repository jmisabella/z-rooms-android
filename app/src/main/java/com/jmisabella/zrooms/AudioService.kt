package com.jmisabella.zrooms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibratorManager
import android.os.VibrationEffect
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.concurrent.timer

class AudioService : Service() {
    private val binder = AudioBinder()
    private lateinit var ambientPlayer: ExoPlayer
    private lateinit var alarmPlayer: ExoPlayer
    private var currentAmbientFile: String? = null
    private var alarmTimer: java.util.Timer? = null
    private var stopTimer: java.util.Timer? = null
    private var hapticGenerator: android.os.Vibrator? = null
    private var isAlarmActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private var audioFocusChangeListener: OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        const val CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    private fun requestAudioFocus(): Boolean {
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                .build()
            this.audioFocusRequest = focusRequest
            result = audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        ambientPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
        }

        alarmPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    stopAll()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    mainHandler.post { ambientPlayer.volume = 0.5f }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mainHandler.post {
                        ambientPlayer.volume = 1f
                        ambientPlayer.play()
                    }
                }
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioFocusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        audioFocusChangeListener = null
        ambientPlayer.release()
        alarmPlayer.release()
        stopTimer?.cancel()
        alarmTimer?.cancel()
        super.onDestroy()
    }

    fun playAmbient(index: Int, durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
        val fileRes = getAmbientResource(index)
        if (fileRes == null) return

        stopAll {
            val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$fileRes")
            ambientPlayer.setMediaItem(mediaItem)
            if (!requestAudioFocus()) {
                // Optionally handle failure, e.g., show toast
                return@stopAll
            }
            mainHandler.post {
                ambientPlayer.prepare()
                ambientPlayer.play()
            }
            fadeAmbientVolume(1f, 2000L)
            currentAmbientFile = "ambient_$index"

            if (durationMinutes > 0) {
                val seconds = (durationMinutes * 60).toLong() * 1000
                stopTimer = timer(initialDelay = seconds, period = seconds) {
                    fadeAmbientVolume(0f, 2000L) {
                        if (isAlarmEnabled) {
                            startAlarm(selectedAlarmIndex)
                        }
                    }
                }
            }
        }
    }

    fun stopAll(completion: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioFocusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        fadeAmbientVolume(0f, 2000L) {
            mainHandler.post { ambientPlayer.stop() }
            completion()
        }
        stopAlarm()
        stopTimer?.cancel()
        stopTimer = null
    }

    fun fadeAmbientVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
        fadeVolume(ambientPlayer, target, duration, completion)
    }

    private fun startAlarm(selectedAlarmIndex: Int?) {
        isAlarmActive = true
        val alarmRes = getAlarmResource(selectedAlarmIndex)
        val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$alarmRes")
        mainHandler.post {
            alarmPlayer.setMediaItem(mediaItem)
            alarmPlayer.prepare()
            alarmPlayer.play()
        }
        fadeVolume(alarmPlayer, 1f, 500L)

        hapticGenerator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        val interval = alarmPlayer.duration
        alarmTimer = timer(initialDelay = interval, period = interval) {
            hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun stopAlarm() {
        if (isAlarmActive) {
            fadeVolume(alarmPlayer, 0f, 2000L) {
                mainHandler.post { alarmPlayer.stop() }
            }
            alarmTimer?.cancel()
            alarmTimer = null
            hapticGenerator = null
            isAlarmActive = false
        }
    }

    private fun fadeVolume(player: ExoPlayer, target: Float, duration: Long, completion: () -> Unit = {}) {
        mainHandler.post {
            val startVolume = player.volume
            val steps = 20
            val stepDuration = duration / steps
            val stepChange = (target - startVolume) / steps.toFloat()
            var currentStep = 0

            val runnable = object : Runnable {
                override fun run() {
                    if (currentStep >= steps) {
                        player.volume = target
                        completion()
                        return
                    }
                    player.volume = startVolume + stepChange * currentStep
                    currentStep++
                    mainHandler.postDelayed(this, stepDuration)
                }
            }
            runnable.run()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Z Rooms")
            .setContentText("Playing ambient audio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getAmbientResource(index: Int): Int? {
        val name = String.format("ambient_%02d", index + 1)
        val id = resources.getIdentifier(name, "raw", packageName)
        return if (id != 0) id else null
    }

    private fun getAlarmResource(index: Int?): Int {
        return if (index != null) {
            getAmbientResource(index) ?: resources.getIdentifier("alarm_01", "raw", packageName)
        } else {
            resources.getIdentifier("alarm_01", "raw", packageName)
        }
    }
}

//package com.jmisabella.zrooms
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.media.AudioManager
//import android.media.AudioFocusRequest
//import android.media.AudioManager.OnAudioFocusChangeListener
//import android.os.Binder
//import android.os.Build
//import android.os.IBinder
//import android.os.VibratorManager
//import android.os.VibrationEffect
//import android.os.Handler
//import android.os.Looper
//import androidx.core.app.NotificationCompat
//import androidx.media3.common.AudioAttributes
//import androidx.media3.common.MediaItem
//import androidx.media3.common.Player
//import androidx.media3.exoplayer.ExoPlayer
//import kotlin.concurrent.timer
//
//
//class AudioService : Service() {
//    private val binder = AudioBinder()
//    private lateinit var ambientPlayer: ExoPlayer
//    private lateinit var alarmPlayer: ExoPlayer
//    private var currentAmbientFile: String? = null
//    private var alarmTimer: java.util.Timer? = null
//    private var stopTimer: java.util.Timer? = null
//    private var hapticGenerator: android.os.Vibrator? = null
//    private var isAlarmActive = false
//    private val mainHandler = Handler(Looper.getMainLooper())
//    private lateinit var audioManager: AudioManager
//    private var audioFocusChangeListener: OnAudioFocusChangeListener? = null
//
//    companion object {
//        const val CHANNEL_ID = "audio_service_channel"
//        const val NOTIFICATION_ID = 1
//    }
//
//    inner class AudioBinder : Binder() {
//        fun getService(): AudioService = this@AudioService
//    }
//
//    private fun requestAudioFocus(): Boolean {
//        val result: Int
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val playbackAttributes = android.media.AudioAttributes.Builder()
//                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
//                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
//                .build()
//            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
//                .setAudioAttributes(playbackAttributes)
//                .setAcceptsDelayedFocusGain(true)
//                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
//                .build()
//            result = audioManager.requestAudioFocus(focusRequest)
//        } else {
//            @Suppress("deprecation")
//            result = audioManager.requestAudioFocus(
//                audioFocusChangeListener,
//                AudioManager.STREAM_MUSIC,
//                AudioManager.AUDIOFOCUS_GAIN
//            )
//        }
//        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
//    }
//
//    override fun onBind(intent: Intent?): IBinder = binder
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//
//        ambientPlayer = ExoPlayer.Builder(this).build().apply {
//            setAudioAttributes(AudioAttributes.DEFAULT, true)
//            repeatMode = Player.REPEAT_MODE_ALL
//            volume = 0f
//        }
//
//        alarmPlayer = ExoPlayer.Builder(this).build().apply {
//            setAudioAttributes(AudioAttributes.DEFAULT, true)
//            repeatMode = Player.REPEAT_MODE_ALL
//            volume = 0f
//        }
//
//        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        audioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->
//            when (focusChange) {
//                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
//                    stopAll()
//                }
//                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
//                    ambientPlayer.volume = 0.5f
//                }
//                AudioManager.AUDIOFOCUS_GAIN -> {
//                    ambientPlayer.volume = 1f
//                    ambientPlayer.play()
//                }
//            }
//        }
//
//        val notification = buildNotification()
//        startForeground(NOTIFICATION_ID, notification)
//    }
//
//    override fun onDestroy() {
//        audioFocusChangeListener = null
//        ambientPlayer.release()
//        alarmPlayer.release()
//        stopTimer?.cancel()
//        alarmTimer?.cancel()
//        super.onDestroy()
//    }
//
//    fun playAmbient(index: Int, durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
//        val fileRes = getAmbientResource(index)
//        if (fileRes == null) return
//
//        stopAll {
//            val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$fileRes")
//            ambientPlayer.setMediaItem(mediaItem)
//            if (!requestAudioFocus()) {
//                // Optionally handle failure, e.g., show toast
//                return@stopAll
//            }
//            ambientPlayer.prepare()
//            ambientPlayer.play()
//            fadeAmbientVolume(1f, 2000L)
//            currentAmbientFile = "ambient_$index"
//
//            if (durationMinutes > 0) {
//                val seconds = (durationMinutes * 60).toLong() * 1000
//                stopTimer = timer(initialDelay = seconds, period = seconds) {
//                    fadeAmbientVolume(0f, 2000L) {
//                        if (isAlarmEnabled) {
//                            startAlarm(selectedAlarmIndex)
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    fun stopAll(completion: () -> Unit = {}) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
//        } else {
//            @Suppress("deprecation")
//            audioManager.abandonAudioFocus(audioFocusChangeListener)
//        }
//        fadeAmbientVolume(0f, 2000L) {
//            ambientPlayer.stop()
//            completion()
//        }
//        stopAlarm()
//        stopTimer?.cancel()
//        stopTimer = null
//    }
//
//    fun fadeAmbientVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
//        fadeVolume(ambientPlayer, target, duration, completion)
//    }
//
//    private fun startAlarm(selectedAlarmIndex: Int?) {
//        isAlarmActive = true
//        val alarmRes = getAlarmResource(selectedAlarmIndex)
//        val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$alarmRes")
//        alarmPlayer.setMediaItem(mediaItem)
//        alarmPlayer.prepare()
//        alarmPlayer.play()
//
//        fadeVolume(alarmPlayer, 1f, 500L)
//
//        hapticGenerator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
//        hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
//
//        val interval = alarmPlayer.duration
//        alarmTimer = timer(initialDelay = interval, period = interval) {
//            hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
//        }
//    }
//
//    fun stopAlarm() {
//        if (isAlarmActive) {
//            fadeVolume(alarmPlayer, 0f, 2000L) {
//                alarmPlayer.stop()
//            }
//            alarmTimer?.cancel()
//            alarmTimer = null
//            hapticGenerator = null
//            isAlarmActive = false
//        }
//    }
//
//    private fun fadeVolume(player: ExoPlayer, target: Float, duration: Long, completion: () -> Unit = {}) {
//        mainHandler.post {
//            val startVolume = player.volume
//            val steps = 20
//            val stepDuration = duration / steps
//            val stepChange = (target - startVolume) / steps.toFloat()
//            var currentStep = 0
//
//            val runnable = object : Runnable {
//                override fun run() {
//                    if (currentStep >= steps) {
//                        player.volume = target
//                        completion()
//                        return
//                    }
//                    player.volume = startVolume + stepChange * currentStep
//                    currentStep++
//                    mainHandler.postDelayed(this, stepDuration)
//                }
//            }
//            runnable.run()
//        }
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(CHANNEL_ID, "Audio Playback", NotificationManager.IMPORTANCE_LOW)
//            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
//        }
//    }
//
//    private fun buildNotification(): Notification {
//        val intent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("Z Rooms")
//            .setContentText("Playing ambient audio")
//            .setSmallIcon(android.R.drawable.ic_media_play)
//            .setContentIntent(pendingIntent)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .build()
//    }
//
//    private fun getAmbientResource(index: Int): Int? {
//        val name = String.format("ambient_%02d", index + 1)
//        val id = resources.getIdentifier(name, "raw", packageName)
//        return if (id != 0) id else null
//    }
//
//    private fun getAlarmResource(index: Int?): Int {
//        return if (index != null) {
//            getAmbientResource(index) ?: resources.getIdentifier("alarm_01", "raw", packageName)
//        } else {
//            resources.getIdentifier("alarm_01", "raw", packageName)
//        }
//    }
//}