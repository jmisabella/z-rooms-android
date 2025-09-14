package com.jmisabella.zrooms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibratorManager
import android.os.VibrationEffect
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

    companion object {
        const val CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
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

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        ambientPlayer.release()
        alarmPlayer.release()
        stopTimer?.cancel()
        alarmTimer?.cancel()
        super.onDestroy()
    }

    fun playAmbient(index: Int, durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
        val fileRes = getAmbientResource(index)
        if (fileRes == null) return

        stopAll()

        val mediaItem = MediaItem.fromUri("android.resource://${packageName}/$fileRes")
        ambientPlayer.setMediaItem(mediaItem)
        ambientPlayer.prepare()
        ambientPlayer.play()

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

    fun stopAll() {
        fadeAmbientVolume(0f, 2000L) {
            ambientPlayer.stop()
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
        alarmPlayer.setMediaItem(mediaItem)
        alarmPlayer.prepare()
        alarmPlayer.play()

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
                alarmPlayer.stop()
            }
            alarmTimer?.cancel()
            alarmTimer = null
            hapticGenerator = null
            isAlarmActive = false
        }
    }

    private fun fadeVolume(player: ExoPlayer, target: Float, duration: Long, completion: () -> Unit = {}) {
        val startVolume = player.volume
        val steps = 20
        val stepDuration = duration / steps
        val stepChange = (target - startVolume) / steps.toFloat()

        var currentStep = 0
        timer(initialDelay = 0, period = stepDuration) {
            currentStep++
            player.volume = startVolume + stepChange * currentStep
            if (currentStep >= steps) {
                player.volume = target
                cancel()
                completion()
            }
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

    private fun getAmbientResource(index: Int): Int? = when (index + 1) {
        1 -> R.raw.ambient_01
        2 -> R.raw.ambient_02
        // Add all 30 resources
        else -> null
    }

    private fun getAlarmResource(index: Int?): Int = index?.let { getAmbientResource(it) } ?: R.raw.alarm_01
}