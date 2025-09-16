package com.jmisabella.zrooms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import java.util.Timer
import kotlin.concurrent.timer

class AudioService : Service() {
    private val binder = AudioBinder()
    private var ambientPlayer: ExoPlayer? = null
    private var alarmPlayer: ExoPlayer? = null
    private var ambientVolume: Float = 0f
    private var alarmVolume: Float = 0f
    private var currentAmbientFile: String? = null
    private var alarmTimer: Timer? = null
    private var stopTimer: Timer? = null
    private var hapticGenerator: android.os.Vibrator? = null
    private var isAlarmActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        const val CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    private val playbackAudioAttributes = ExoAudioAttributes.Builder()
        .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
        .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private fun requestAudioFocus(): Boolean {
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                .build()
            audioFocusRequest = focusRequest
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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    stopAll()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    mainHandler.post { setAmbientVolume(0.5f) }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mainHandler.post {
                        setAmbientVolume(1f)
                        if (ambientPlayer?.isPlaying == false) {
                            ambientPlayer?.play()
                        }
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
        ambientPlayer?.release()
        alarmPlayer?.release()
        stopTimer?.cancel()
        alarmTimer?.cancel()
        super.onDestroy()
    }

    fun playAmbient(index: Int, durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
        val fileRes = getAmbientResource(index) ?: return

        stopAll {
            ambientPlayer = ExoPlayer.Builder(this@AudioService).build().apply {
                setAudioAttributes(playbackAudioAttributes, false) // Set to false to manage focus manually
                val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${packageName}/$fileRes"))
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                volume = 0f
            }
            ambientVolume = 0f

            if (!requestAudioFocus()) {
                ambientPlayer?.release()
                ambientPlayer = null
                return@stopAll
            }

            ambientPlayer?.play()
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
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioFocusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        fadeAmbientVolume(0f, 2000L) {
            ambientPlayer?.stop()
            ambientPlayer?.release()
            ambientPlayer = null
            ambientVolume = 0f
            completion()
        }
        stopAlarm()
        stopTimer?.cancel()
        stopTimer = null
    }

    fun fadeAmbientVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
        fadeVolume(ambientPlayer, ambientVolume, target, duration, { vol -> setAmbientVolume(vol) }, completion)
    }

    private fun startAlarm(selectedAlarmIndex: Int?) {
        isAlarmActive = true
        val alarmRes = getAlarmResource(selectedAlarmIndex)
        alarmPlayer = ExoPlayer.Builder(this@AudioService).build().apply {
            setAudioAttributes(playbackAudioAttributes, false) // Set to false
            val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${packageName}/$alarmRes"))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            volume = 0f
        }
        alarmVolume = 0f
        alarmPlayer?.play()
        fadeVolume(alarmPlayer, alarmVolume, 1f, 500L, { vol -> setAlarmVolume(vol) })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            hapticGenerator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            hapticGenerator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        // For alarm duration, wait until player is ready (ExoPlayer may not have duration immediately)
        var interval: Long = 1000L // Fallback
        alarmPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    interval = alarmPlayer?.duration ?: 1000L
                    alarmTimer?.cancel()
                    alarmTimer = timer(initialDelay = interval, period = interval) {
                        hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            }
        })
    }

    fun stopAlarm() {
        if (isAlarmActive) {
            fadeVolume(alarmPlayer, alarmVolume, 0f, 2000L, { vol -> setAlarmVolume(vol) }) {
                alarmPlayer?.stop()
                alarmPlayer?.release()
                alarmPlayer = null
                alarmVolume = 0f
            }
            alarmTimer?.cancel()
            alarmTimer = null
            hapticGenerator = null
            isAlarmActive = false
        }
    }

    private fun fadeVolume(
        player: ExoPlayer?,
        startVolume: Float,
        target: Float,
        duration: Long,
        setVol: (Float) -> Unit,
        completion: () -> Unit = {}
    ) {
        if (player == null) {
            completion()
            return
        }
        val steps = 20
        val stepDuration = duration / steps
        val stepChange = (target - startVolume) / steps.toFloat()
        var currentStep = 0

        val runnable = object : Runnable {
            override fun run() {
                if (currentStep >= steps) {
                    setVol(target)
                    completion()
                    return
                }
                setVol(startVolume + stepChange * currentStep)
                currentStep++
                mainHandler.postDelayed(this, stepDuration)
            }
        }
        mainHandler.post(runnable)
    }

    private fun setAmbientVolume(vol: Float) {
        ambientPlayer?.volume = vol
        ambientVolume = vol
    }

    private fun setAlarmVolume(vol: Float) {
        alarmPlayer?.volume = vol
        alarmVolume = vol
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
