package com.jmisabella.zrooms

import java.util.Timer
import java.util.TimerTask
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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import java.util.Locale
import kotlin.concurrent.timer

class AudioService : Service() {
    private val binder = AudioBinder()
    private var ambientPlayer: ExoPlayer? = null
    private var alarmPlayer: ExoPlayer? = null
    private var previewPlayer: ExoPlayer? = null
    private var ambientVolume: Float = 0f
    private var targetAmbientVolume: Float = 0.8f // User's preferred ambient volume (default to 80%)
    private var alarmVolume: Float = 0f
    private var previewVolume: Float = 0f
    private var currentAmbientFile: String? = null
    private var alarmTimer: Timer? = null
    private var stopTimer: Timer? = null
    private var hapticGenerator: android.os.Vibrator? = null
    private var isAlarmActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var shouldStartPreview = false
    private var isTransitioning = false
    private var currentAmbientFade: Runnable? = null
    private var currentAlarmFade: Runnable? = null
    private var currentPreviewFade: Runnable? = null

    // Wake-up greeting TTS
    private var greetingTts: TextToSpeech? = null
    private var isGreetingTtsInitialized = false

    companion object {
        const val CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1

        // Wake-up greeting phrases
        private val GREETING_PHRASES = arrayOf(
            "Welcome back",
            "Greetings",
            "Here we are",
            "Returning to awareness",
            "Welcome back to this space"
        )
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    fun isReady(): Boolean = !isTransitioning

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
                        setAmbientVolume(targetAmbientVolume)
                        if (ambientPlayer?.isPlaying == false) {
                            ambientPlayer?.play()
                        }
                    }
                }
            }
        }

        // Initialize wake-up greeting TTS
        greetingTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                greetingTts?.language = Locale.US
                greetingTts?.setSpeechRate(0.6f) // Same as meditation speech rate
                greetingTts?.setPitch(0.58f) // Same as meditation pitch
                isGreetingTtsInitialized = true
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
        previewPlayer?.release()
        stopTimer?.cancel()
        alarmTimer?.cancel()
        currentAmbientFade?.let { mainHandler.removeCallbacks(it) }
        currentAlarmFade?.let { mainHandler.removeCallbacks(it) }
        currentPreviewFade?.let { mainHandler.removeCallbacks(it) }

        // Clean up greeting TTS
        greetingTts?.stop()
        greetingTts?.shutdown()
        greetingTts = null

        super.onDestroy()
    }
    fun playAmbient(index: Int, durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
        if (isTransitioning) return
        isTransitioning = true
        val fileRes = getAmbientResource(index) ?: run { isTransitioning = false; return }
        stopAll {
            ambientPlayer = ExoPlayer.Builder(this@AudioService).build().apply {
                setAudioAttributes(playbackAudioAttributes, false)
                val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${packageName}/$fileRes"))
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                volume = 0f
                val fadeListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            play()
                            fadeAmbientVolume(targetAmbientVolume, 2000L)
                            removeListener(this)
                        }
                    }
                }
                addListener(fadeListener)
            }
            ambientVolume = 0f
            if (!requestAudioFocus()) {
                ambientPlayer?.release()
                ambientPlayer = null
                isTransitioning = false
                return@stopAll
            }
            currentAmbientFile = "ambient_${index + 1}"
            updateTimer(durationMinutes, isAlarmEnabled, selectedAlarmIndex)
            isTransitioning = false
        }
    }
    fun updateTimer(durationMinutes: Double, isAlarmEnabled: Boolean, selectedAlarmIndex: Int?) {
        stopTimer?.cancel()
        stopTimer = null
        if (durationMinutes > 0) {
            val delayMillis = (durationMinutes * 60 * 1000).toLong()
            val capturedIsAlarmEnabled = isAlarmEnabled
            val capturedSelectedAlarmIndex = selectedAlarmIndex
            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    mainHandler.post {
                        stopAll {
                            if (capturedIsAlarmEnabled && capturedSelectedAlarmIndex != null) {
                                startAlarm(capturedSelectedAlarmIndex)
                            }
                        }
                    }
                }
            }, delayMillis)
            stopTimer = timer
        }
    }
    fun playPreview(index: Int) {
        val fileRes = getAmbientResource(index) ?: return
        stopPreview(restoreAmbient = false) {
            shouldStartPreview = true
            fadeAmbientVolume(0f, 1000L) {
                if (!shouldStartPreview) return@fadeAmbientVolume
                previewPlayer = ExoPlayer.Builder(this@AudioService).build().apply {
                    setAudioAttributes(playbackAudioAttributes, false)
                    val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${packageName}/$fileRes"))
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_OFF
                    prepare()
                    volume = 0f
                    val fadeListener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                play()
                                fadePreviewVolume(1f, 1000L)
                                removeListener(this)
                            }
                        }
                    }
                    addListener(fadeListener)
                }
                previewVolume = 0f
            }
        }
    }
    fun stopPreview(restoreAmbient: Boolean = true, completion: () -> Unit = {}) {
        shouldStartPreview = false
        fadePreviewVolume(0f, 1000L) {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
            previewVolume = 0f
            if (restoreAmbient && ambientPlayer != null) {
                fadeAmbientVolume(1f, 1000L)
            }
            completion()
        }
    }

    fun fadePreviewVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
        fadeVolume(previewPlayer, previewVolume, target, duration, { vol -> setPreviewVolume(vol) }, completion)
    }

    private fun setPreviewVolume(vol: Float) {
        previewPlayer?.volume = vol
        previewVolume = vol
    }

    fun stopAll(completion: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioFocusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        // Cancel current fades
        currentAmbientFade?.let { mainHandler.removeCallbacks(it) }
        currentAlarmFade?.let { mainHandler.removeCallbacks(it) }
        currentPreviewFade?.let { mainHandler.removeCallbacks(it) }
        currentAmbientFade = null
        currentAlarmFade = null
        currentPreviewFade = null
        var pending = 0
        if (ambientPlayer != null) pending++
        if (previewPlayer != null) pending++
        if (isAlarmActive) pending++
        if (pending == 0) {
            isTransitioning = false
            completion()
            return
        }
        val done = {
            pending--
            if (pending == 0) {
                isTransitioning = false
                completion()
            }
        }
        if (ambientPlayer != null) {
            fadeAmbientVolume(0f, 200L) {
                ambientPlayer?.stop()
                ambientPlayer?.release()
                ambientPlayer = null
                ambientVolume = 0f
                done()
            }
        } else {
// If no ambient, but counted? No, if null, not incremented
        }
        if (previewPlayer != null) {
            fadePreviewVolume(0f, 200L) {
                previewPlayer?.stop()
                previewPlayer?.release()
                previewPlayer = null
                previewVolume = 0f
                done()
            }
        }
        if (isAlarmActive) {
            fadeAlarmVolume(0f, 2000L) {
                alarmPlayer?.stop()
                alarmPlayer?.release()
                alarmPlayer = null
                alarmVolume = 0f
                alarmTimer?.cancel()
                alarmTimer = null
                hapticGenerator = null
                isAlarmActive = false
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("com.jmisabella.zrooms.ALARM_STOPPED"))
                done()
            }
        }
        stopTimer?.cancel()
        stopTimer = null
    }

    fun fadeAmbientVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
        fadeVolume(ambientPlayer, ambientVolume, target, duration, { vol -> setAmbientVolume(vol) }, completion)
    }

    private fun startAlarm(selectedAlarmIndex: Int?) {
        if (selectedAlarmIndex == null || selectedAlarmIndex == -1) {
            // No alarm (SILENCE): Just fade ambient out, no greeting
            fadeAmbientVolume(0f, 2000L) {
                ambientPlayer?.stop()
                ambientPlayer?.release()
                ambientPlayer = null
                ambientVolume = 0f
            }
            return
        }
        isAlarmActive = true
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("com.jmisabella.zrooms.ALARM_STARTED"))

        // Check if we should play wake-up greeting
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val meditationCompleted = prefs.getBoolean(TextToSpeechManager.PREF_MEDITATION_COMPLETED, false)

        val alarmRes = getAlarmResource(selectedAlarmIndex)
        alarmPlayer = ExoPlayer.Builder(this@AudioService).build().apply {
            setAudioAttributes(playbackAudioAttributes, false)
            val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${packageName}/$alarmRes"))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            volume = 0f
            val fadeListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        play()
                        fadeAlarmVolume(1f, 500L)
                        removeListener(this)

                        // Schedule wake-up greeting if meditation was completed
                        if (meditationCompleted) {
                            scheduleWakeUpGreeting()
                        }
                    }
                }
            }
            addListener(fadeListener)
        }
        alarmVolume = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            hapticGenerator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            hapticGenerator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        hapticGenerator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        var interval: Long = 1000L
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

    /**
     * Schedules the wake-up greeting to play 5 seconds after alarm starts
     * Only plays once, then clears the meditation completion flag
     */
    private fun scheduleWakeUpGreeting() {
        if (!isGreetingTtsInitialized) return

        mainHandler.postDelayed({
            if (isAlarmActive) { // Only play if alarm is still active
                playWakeUpGreeting()
            }
        }, 5000L) // 5 seconds delay
    }

    /**
     * Plays a random wake-up greeting phrase using TTS
     */
    private fun playWakeUpGreeting() {
        if (!isGreetingTtsInitialized) return

        // Select a random greeting phrase
        val greeting = GREETING_PHRASES.random()

        // Set up utterance parameters
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "wake_up_greeting"
        params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = VoiceManager.VOICE_VOLUME.toString()

        // Speak the greeting
        greetingTts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, params)

        // Clear the meditation completion flag after greeting plays
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean(TextToSpeechManager.PREF_MEDITATION_COMPLETED, false).apply()
    }

    fun fadeAlarmVolume(target: Float, duration: Long, completion: () -> Unit = {}) {
        fadeVolume(alarmPlayer, alarmVolume, target, duration, { vol -> setAlarmVolume(vol) }, completion)
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
                    when (player) {
                        ambientPlayer -> currentAmbientFade = null
                        alarmPlayer -> currentAlarmFade = null
                        previewPlayer -> currentPreviewFade = null
                    }
                    return
                }
                setVol(startVolume + stepChange * currentStep)
                currentStep++
                mainHandler.postDelayed(this, stepDuration)
            }
        }
        when (player) {
            ambientPlayer -> currentAmbientFade = runnable
            alarmPlayer -> currentAlarmFade = runnable
            previewPlayer -> currentPreviewFade = runnable
        }
        mainHandler.post(runnable)
    }

    fun setAmbientVolume(vol: Float) {
        targetAmbientVolume = vol.coerceIn(0f, 1.0f) // Store user's preferred volume (max 1.0)
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


