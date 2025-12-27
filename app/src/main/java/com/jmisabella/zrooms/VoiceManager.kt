package com.jmisabella.zrooms

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.mutableStateOf
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * Manages TTS voice selection, quality detection, and speech parameters.
 * Provides dynamic speech rate based on voice quality and persistent voice preferences.
 */
class VoiceManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var previewTTS: TextToSpeech? = null

    // Observable state for UI
    var availableVoices = mutableStateOf<List<Voice>>(emptyList())
        private set

    var useEnhancedVoice = mutableStateOf(false)
        private set

    var selectedVoice = mutableStateOf<Voice?>(null)
        private set

    companion object {
        @Volatile
        private var INSTANCE: VoiceManager? = null

        // SharedPreferences keys
        private const val PREF_USE_ENHANCED_VOICE = "use_enhanced_voice"
        private const val PREF_PREFERRED_VOICE_NAME = "preferred_voice_name"

        // Speech rate multipliers based on voice quality
        private const val DEFAULT_VOICE_RATE = 0.8f  // Slower for robotic voices
        private const val ENHANCED_VOICE_RATE = 1.0f // Natural speed for high-quality voices

        // Voice volume (shared across meditation playback and preview)
        const val VOICE_VOLUME = 0.23f

        fun getInstance(context: Context): VoiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadPreferences()
        initializeTTS()
    }

    private fun loadPreferences() {
        useEnhancedVoice.value = prefs.getBoolean(PREF_USE_ENHANCED_VOICE, false)
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                discoverVoices()
                restoreSelectedVoice()
            }
        }
    }

    /**
     * Discovers all available voices and filters for English voices
     */
    private fun discoverVoices() {
        val voices = tts?.voices?.filter { voice ->
            // Filter for English voices and offline-capable voices only
            voice.locale.language == "en" && !voice.isNetworkConnectionRequired
        }?.sortedWith(compareByDescending<Voice> {
            // Sort by quality (high to low)
            it.quality
        }.thenBy {
            // Then by locale (US first)
            if (it.locale == Locale.US) 0 else 1
        }) ?: emptyList()

        availableVoices.value = voices
    }

    /**
     * Restores the previously selected voice from preferences
     */
    private fun restoreSelectedVoice() {
        val voiceName = prefs.getString(PREF_PREFERRED_VOICE_NAME, null)
        if (voiceName != null && useEnhancedVoice.value) {
            selectedVoice.value = availableVoices.value.find { it.name == voiceName }
        }
    }

    /**
     * Gets the preferred voice for meditation narration
     * Priority: User's selected enhanced voice -> Any high-quality voice -> Default system voice
     */
    fun getPreferredVoice(): Voice? {
        if (!isInitialized) return null

        // If enhanced voice is disabled, use default system voice (null = system default)
        if (!useEnhancedVoice.value) {
            return null
        }

        // Try to use the selected enhanced voice
        selectedVoice.value?.let { voice ->
            if (isVoiceAvailable(voice)) {
                return voice
            }
        }

        // Fallback to any available high-quality voice for English (US preferred)
        val highQualityVoice = availableVoices.value.firstOrNull { voice ->
            voice.quality >= Voice.QUALITY_HIGH && isVoiceAvailable(voice)
        }

        if (highQualityVoice != null) {
            return highQualityVoice
        }

        // Final fallback to default system voice
        return null
    }

    /**
     * Gets the speech rate multiplier based on voice quality
     * Enhanced/Premium voices sound better at natural speed (1.0x)
     * Default voices sound better slowed down (0.8x)
     */
    fun getSpeechRateMultiplier(voice: Voice?): Float {
        // If no voice specified or enhanced voice disabled, use slower rate for default voice
        if (voice == null || !useEnhancedVoice.value) {
            return DEFAULT_VOICE_RATE
        }

        // Enhanced and high-quality voices sound better at normal speed
        return if (voice.quality >= Voice.QUALITY_HIGH) {
            ENHANCED_VOICE_RATE
        } else {
            DEFAULT_VOICE_RATE
        }
    }

    /**
     * Checks if a voice is currently available on the device
     */
    private fun isVoiceAvailable(voice: Voice): Boolean {
        return try {
            tts?.setVoice(voice) == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Updates the enhanced voice preference
     */
    fun setUseEnhancedVoice(enabled: Boolean) {
        useEnhancedVoice.value = enabled
        prefs.edit().putBoolean(PREF_USE_ENHANCED_VOICE, enabled).apply()

        if (!enabled) {
            // Clear selected voice when disabling enhanced voice
            selectedVoice.value = null
            prefs.edit().remove(PREF_PREFERRED_VOICE_NAME).apply()
        }
    }

    /**
     * Sets the preferred voice and persists the selection
     * Automatically enables enhanced voice when a voice is selected
     */
    fun setPreferredVoice(voice: Voice?) {
        selectedVoice.value = voice
        if (voice != null) {
            // Automatically enable enhanced voice when a voice is selected
            useEnhancedVoice.value = true
            prefs.edit()
                .putString(PREF_PREFERRED_VOICE_NAME, voice.name)
                .putBoolean(PREF_USE_ENHANCED_VOICE, true)
                .apply()
        } else {
            prefs.edit().remove(PREF_PREFERRED_VOICE_NAME).apply()
        }
    }

    /**
     * Gets a friendly display name for a voice
     * Android voice names are often cryptic, so we try to make them readable
     * Similar to iOS where voices have names like "Samantha", "Alex", "Karen"
     *
     * Maps Google TTS voice codes to friendly names based on actual voice characteristics.
     * Codes derived from: https://cloud.google.com/text-to-speech/docs/voices
     */
    fun getFriendlyVoiceName(voice: Voice): String {
        val name = voice.name.lowercase()

        // Extract the voice code (e.g., "tpf" from "en-us-x-tpf-local")
        val voiceCode = when {
            name.contains("-x-") -> {
                name.substringAfter("-x-").substringBefore("-").substringBefore("#")
            }
            else -> ""
        }

        // Map Google TTS voice codes to friendly names
        // Based on actual gender characteristics of Google's en-US voices
        return when (voiceCode) {
            // Female voices (Google TTS en-US)
            "sfg" -> "Samantha"      // Standard Female G
            "iob" -> "Emily"         // IOB Female
            "iog" -> "Victoria"      // IOG Female

            // Male voices (Google TTS en-US)
            "tpf" -> "Alex"          // TPF Male
            "iom" -> "Daniel"        // IOM Male
            "tpd" -> "Michael"       // TPD Male
            "tpc" -> "James"         // TPC Male

            // Fallback for language voice or unknown
            "language" -> {
                val voiceNum = availableVoices.value.indexOf(voice) + 1
                "Voice $voiceNum"
            }

            else -> {
                // Unknown voice code - assign generic name by position
                val voiceNum = availableVoices.value.indexOf(voice) + 1
                when (voiceNum) {
                    1 -> "Voice A"
                    2 -> "Voice B"
                    3 -> "Voice C"
                    4 -> "Voice D"
                    5 -> "Voice E"
                    6 -> "Voice F"
                    7 -> "Voice G"
                    8 -> "Voice H"
                    else -> "Voice $voiceNum"
                }
            }
        }
    }

    /**
     * Gets a quality badge label for UI display
     */
    fun getQualityLabel(voice: Voice): String {
        return when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> "Premium"
            Voice.QUALITY_HIGH -> "Enhanced"
            Voice.QUALITY_NORMAL -> "Standard"
            Voice.QUALITY_LOW -> "Low"
            Voice.QUALITY_VERY_LOW -> "Very Low"
            else -> "Unknown"
        }
    }

    /**
     * Gets a quality badge color for UI display
     */
    fun getQualityColor(voice: Voice): androidx.compose.ui.graphics.Color {
        return when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> androidx.compose.ui.graphics.Color(0xFFFFD700) // Gold
            Voice.QUALITY_HIGH -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            Voice.QUALITY_NORMAL -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
            else -> androidx.compose.ui.graphics.Color(0xFF757575) // Dark Gray
        }
    }

    /**
     * Checks if a voice needs to be downloaded
     * On Android, most voices come pre-installed, but some may need download
     */
    fun needsDownload(voice: Voice): Boolean {
        // In Android, we can't directly check if a voice needs download
        // We approximate by checking if the voice is available
        return !isVoiceAvailable(voice)
    }

    /**
     * Preview a voice by speaking sample text
     */
    fun previewVoice(voice: Voice, text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) return

        // Stop any existing preview
        stopPreview()

        previewTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                previewTTS?.setVoice(voice)
                previewTTS?.setSpeechRate(getSpeechRateMultiplier(voice))
                previewTTS?.setPitch(1.0f)

                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "preview"
                params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = VOICE_VOLUME.toString()

                previewTTS?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        previewTTS?.shutdown()
                        previewTTS = null
                        onComplete?.invoke()
                    }
                    override fun onError(utteranceId: String?) {
                        previewTTS?.shutdown()
                        previewTTS = null
                        onComplete?.invoke()
                    }
                })

                previewTTS?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
        }
    }

    /**
     * Stops any ongoing preview
     */
    fun stopPreview() {
        previewTTS?.stop()
        previewTTS?.shutdown()
        previewTTS = null
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        stopPreview()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
