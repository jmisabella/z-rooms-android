package com.jmisabella.zrooms

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
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
        private const val PREF_VOICE_MIGRATION_V1 = "voice_migration_v1_completed"

        // Speech rate multipliers based on voice quality
        private const val DEFAULT_VOICE_RATE = 0.8f  // Slower for robotic voices
        private const val ENHANCED_VOICE_RATE = 1.0f // Natural speed for high-quality voices

        // Voice volume (shared across story playback and preview)
        const val VOICE_VOLUME = 0.20f

        // Voice filtering - Accept-list for story-appropriate voices
        private val ACCEPTED_LOCALES = listOf(
            "en-GB",  // British English (highest priority - serious tone)
            "en-AU",  // Australian English (neutral, narrative-friendly)
            "en-IN",  // Indian English (deeper voices available)
            "en-US"   // American English (lowest priority - many too bright)
        )

        // Priority order for automatic voice selection
        private val LOCALE_PRIORITY_ORDER = listOf(
            "en-GB",
            "en-AU",
            "en-IN",
            "en-US"
        )

        fun getInstance(context: Context): VoiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        performOneTimeMigration()
        loadPreferences()
        initializeTTS()
    }

    /**
     * One-time migration to reset voice preferences to smart defaults
     * This allows all users to benefit from Daniel voice auto-detection
     */
    private fun performOneTimeMigration() {
        val migrationCompleted = prefs.getBoolean(PREF_VOICE_MIGRATION_V1, false)
        if (!migrationCompleted) {
            // Clear existing voice preferences to apply smart defaults
            prefs.edit()
                .remove(PREF_USE_ENHANCED_VOICE)
                .remove(PREF_PREFERRED_VOICE_NAME)
                .putBoolean(PREF_VOICE_MIGRATION_V1, true)
                .apply()
        }
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
     * Determines if a voice meets the accept-list criteria for story narration
     * Filters based on locale, quality, and offline availability
     */
    private fun isVoiceAccepted(voice: Voice): Boolean {
        val locale = voice.locale

        // Check if locale is in accept-list
        val localeString = "${locale.language}-${locale.country}"
        val isLocaleAccepted = ACCEPTED_LOCALES.any {
            localeString.equals(it, ignoreCase = true)
        }

        if (!isLocaleAccepted) return false

        // Must not require network (offline requirement)
        if (voice.isNetworkConnectionRequired) return false

        // Filter by quality if available (exclude low-quality voices)
        // Note: Some devices may not properly report quality, so we don't fail if quality is unknown
        if (voice.quality == Voice.QUALITY_LOW) return false

        return true
    }

    /**
     * Discovers available TTS voices on device
     * Filters to story-appropriate voices only (British, Australian, Indian, American English)
     */
    private fun discoverVoices() {
        if (!isInitialized) return

        val allVoices = tts?.voices ?: emptySet()

        // Filter using accept-list
        val filtered = allVoices
            .filter { isVoiceAccepted(it) }
            .sortedWith(
                compareBy<Voice> { voice ->
                    // Sort by locale priority
                    val localeString = "${voice.locale.language}-${voice.locale.country}"
                    val priorityIndex = LOCALE_PRIORITY_ORDER.indexOfFirst {
                        localeString.equals(it, ignoreCase = true)
                    }
                    if (priorityIndex == -1) Int.MAX_VALUE else priorityIndex
                }
                .thenByDescending { it.quality }  // Then by quality (highest first)
            )

        availableVoices.value = filtered

        // Log for debugging
        Log.d("VoiceManager", "Discovered ${filtered.size} story-appropriate voices (filtered from ${allVoices.size} total)")
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
     * Gets the preferred voice for story narration
     * Priority hierarchy:
     * 1. User's explicitly selected voice (if still available and accepted)
     * 2. Locale-based hierarchy (en-GB > en-AU > en-IN > en-US)
     * 3. Previously auto-selected voice (backward compatibility)
     * 4. Random from story-appropriate list
     * 5. System default (last resort)
     */
    fun getPreferredVoice(): Voice? {
        if (!isInitialized) return null

        // STEP 1: Check if user explicitly selected a voice (when enhanced voice is enabled)
        if (useEnhancedVoice.value) {
            selectedVoice.value?.let { voice ->
                if (isVoiceAvailable(voice) && isVoiceAccepted(voice)) {
                    return voice
                } else {
                    // User's voice no longer available or not accepted - clear selection
                    selectedVoice.value = null
                }
            }
        }

        // STEP 2: Try voice hierarchy (locale-based priority)
        val acceptedVoices = availableVoices.value.filter { isVoiceAccepted(it) }
        val hierarchyVoice = getVoiceFromHierarchy(acceptedVoices.toSet())
        if (hierarchyVoice != null) {
            return hierarchyVoice
        }

        // STEP 3: Check previously auto-selected voice (backward compatibility)
        val savedVoiceName = prefs.getString(PREF_PREFERRED_VOICE_NAME, null)
        if (savedVoiceName != null) {
            val voice = tts?.voices?.find { it.name == savedVoiceName }
            if (voice != null && isVoiceAvailable(voice) && isVoiceAccepted(voice)) {
                return voice
            }
        }

        // STEP 4: Random from story-appropriate list (provides variety)
        if (acceptedVoices.isNotEmpty()) {
            return acceptedVoices.random()
        }

        // STEP 5: System default fallback (edge case: no accepted voices available)
        Log.w("VoiceManager", "No accepted voices available - falling back to system default")
        return null  // null triggers system default
    }

    /**
     * Helper: Gets the highest-priority voice from locale hierarchy
     */
    private fun getVoiceFromHierarchy(availableVoices: Set<Voice>): Voice? {
        // Try each locale in priority order
        for (localePrefix in LOCALE_PRIORITY_ORDER) {
            // Find best quality voice for this locale
            val voicesForLocale = availableVoices
                .filter { voice ->
                    val localeString = "${voice.locale.language}-${voice.locale.country}"
                    localeString.equals(localePrefix, ignoreCase = true)
                }
                .sortedByDescending { it.quality }

            // Return first (highest quality) voice found
            if (voicesForLocale.isNotEmpty()) {
                return voicesForLocale.first()
            }
        }

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
