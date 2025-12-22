package com.jmisabella.zrooms

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A manager for text-to-speech using Android's TextToSpeech engine.
 * Supports pause patterns like "(3s)" for 3 seconds and "(1.5m)" for 1.5 minutes.
 */
class TextToSpeechManager(
    private val context: Context,
    private val customMeditationManager: CustomMeditationManager? = null
) {
    var isSpeaking by mutableStateOf(false)
        private set

    var isPlayingMeditation by mutableStateOf(false)
        private set

    var ambientVolume by mutableStateOf(1.0f) // 0.0 (silent) to 1.0 (full ambient), default to 100%
        private set

    var currentPhrase by mutableStateOf("")
        private set

    var previousPhrase by mutableStateOf("")
        private set

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var utteranceQueue = mutableListOf<Pair<String, Long>>() // (phrase, delay in ms)
    private var currentUtteranceIndex = 0
    private var isCustomMode = false
    private var pendingPhrase: String? = null // Phrase waiting to be displayed when TTS starts
    private val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

    // Callback to notify when ambient volume changes
    var onAmbientVolumeChanged: ((Float) -> Unit)? = null

    companion object {
        private const val MEDITATION_SPEECH_RATE = 0.6f // Calm, slow rate (increased from 0.33 by ~20%)
        private const val MEDITATION_PITCH = 0.58f // Lower pitch for calmer voice (decreased from 0.9)
        const val VOICE_VOLUME = 0.23f // Voice volume (fixed, cannot be changed dynamically)
        const val MAX_AMBIENT_VOLUME = 1.0f // Maximum ambient volume
        const val PREF_MEDITATION_COMPLETED = "meditationCompletedSuccessfully"
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(MEDITATION_SPEECH_RATE)
                tts?.setPitch(MEDITATION_PITCH)
                isInitialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        // Update caption when TTS actually starts speaking
                        pendingPhrase?.let { phrase ->
                            previousPhrase = currentPhrase
                            currentPhrase = phrase
                            pendingPhrase = null
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        didFinishSpeaking()
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        isPlayingMeditation = false
                        pendingPhrase = null
                    }
                })
            }
        }
    }

    fun updateAmbientVolume(newVolume: Float) {
        ambientVolume = newVolume.coerceIn(0f, MAX_AMBIENT_VOLUME)
        onAmbientVolumeChanged?.invoke(ambientVolume)
    }

    /**
     * Starts speaking text with embedded pauses like "(4s)" or "(1.5m)"
     * Splits into utterances automatically with appropriate delays
     */
    fun startSpeakingWithPauses(text: String) {
        if (!isInitialized || text.isEmpty()) return

        // Stop any existing speech first to ensure clean state
        stopSpeaking()

        // Clear the meditation completion flag when starting a new meditation
        prefs.edit().putBoolean(PREF_MEDITATION_COMPLETED, false).apply()

        isSpeaking = true
        isPlayingMeditation = true
        isCustomMode = true

        // Check if text has any pause markers
        val hasPauseMarkers = Regex("""\(\d+(?:\.\d+)?[sm]\)""").containsMatchIn(text)

        // If no pause markers found, add automatic ones
        val processedText = if (hasPauseMarkers) text else addAutomaticPauses(text)

        // Extract phrases with pauses
        utteranceQueue = extractPhrasesWithPauses(processedText).toMutableList()
        currentUtteranceIndex = 0

        speakNextPhrase()
    }

    /**
     * Starts speaking a random meditation from text files in res/raw
     * Always picks a new random meditation, stopping any existing playback
     */
    fun startSpeakingRandomMeditation(): String? {
        if (!isInitialized) return null

        // Try to load a random meditation file
        val meditationText = loadRandomMeditationFile() ?: run {
            println("No meditation files found")
            return null
        }

        startSpeakingWithPauses(meditationText)
        return meditationText
    }

    /**
     * Stops speaking immediately
     */
    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
        isPlayingMeditation = false
        utteranceQueue.clear()
        currentUtteranceIndex = 0
        currentPhrase = ""
        previousPhrase = ""
        pendingPhrase = null

        // Clear the meditation completion flag when manually stopping
        prefs.edit().putBoolean(PREF_MEDITATION_COMPLETED, false).apply()
    }

    /**
     * Automatically adds pauses to text: 2s after sentences, 4s after paragraphs
     */
    private fun addAutomaticPauses(text: String): String {
        val result = StringBuilder()
        val paragraphs = text.split("\n")

        for ((index, paragraph) in paragraphs.withIndex()) {
            val trimmed = paragraph.trim()

            // Skip empty lines
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }

            // Split into sentences
            val sentences = trimmed.split(Regex("[.!?]"))

            for (sentence in sentences) {
                val trimmedSentence = sentence.trim()
                if (trimmedSentence.isEmpty()) continue

                // Check if this sentence already has a pause marker
                if (!Regex("""\(\d+(?:\.\d+)?s\)\s*${'$'}""").containsMatchIn(trimmedSentence)) {
                    // No pause found, add automatic 2s pause
                    result.append(trimmedSentence).append(" (2s)\n")
                } else {
                    // Already has a pause, keep it
                    result.append(trimmedSentence).append("\n")
                }
            }

            // Add longer pause between paragraphs (except after the last one)
            if (index < paragraphs.size - 1) {
                result.append("(4s)\n")
            }
        }

        return result.toString()
    }

    /**
     * Extracts phrases and their associated pauses from text
     * Returns list of (phrase, delay in milliseconds)
     */
    private fun extractPhrasesWithPauses(text: String): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()

        // Pattern to match pause markers: (3s), (2.5s), (1m), (1.5m), etc.
        val pattern = Regex("""\((\d+(?:\.\d+)?)(s|m)\)""")
        val matches = pattern.findAll(text).toList()

        var lastIndex = 0

        for (match in matches) {
            // Get the phrase before this pause marker
            val phraseBeforePause = text.substring(lastIndex, match.range.first).trim()

            // Get the pause duration and unit
            val value = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = match.groupValues[2]

            // Convert to milliseconds
            val milliseconds = when (unit) {
                "m" -> (value * 60.0 * 1000).toLong()
                "s" -> (value * 1000).toLong()
                else -> (value * 1000).toLong()
            }

            if (phraseBeforePause.isNotEmpty()) {
                result.add(phraseBeforePause to milliseconds)
            }

            lastIndex = match.range.last + 1
        }

        // Add any remaining text after the last pause marker
        val remainingText = text.substring(lastIndex).trim()
        if (remainingText.isNotEmpty()) {
            result.add(remainingText to 0L)
        }

        return result
    }

    /**
     * Loads a random meditation from both preset files and custom meditations
     */
    private fun loadRandomMeditationFile(): String? {
        // Collect all available meditations
        val allMeditations = mutableListOf<String>()

        // 1. Load all preset meditation files from res/raw
        // Dynamically discover all preset_meditation files without hardcoded limit
        var i = 1
        while (true) {
            val resId = context.resources.getIdentifier(
                "preset_meditation$i",
                "raw",
                context.packageName
            )
            if (resId == 0) {
                // No more preset meditation files found
                break
            }
            try {
                val text = context.resources.openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
                if (text.isNotEmpty()) {
                    allMeditations.add(text)
                }
            } catch (e: Exception) {
                println("Could not read preset meditation $i: ${e.message}")
            }
            i++
        }

        // 2. Add all custom meditations
        customMeditationManager?.meditations?.forEach { meditation ->
            if (meditation.text.isNotEmpty()) {
                allMeditations.add(meditation.text)
            }
        }

        // 3. Check if we have any meditations at all
        if (allMeditations.isEmpty()) {
            println("No meditation files found (neither preset nor custom)")
            return null
        }

        // 4. Pick a random meditation from all available ones
        return allMeditations.random()
    }

    /**
     * Speaks the next phrase in the queue
     */
    private fun speakNextPhrase() {
        if (!isCustomMode || currentUtteranceIndex >= utteranceQueue.size) {
            isSpeaking = false
            isPlayingMeditation = false
            previousPhrase = currentPhrase
            currentPhrase = ""
            pendingPhrase = null
            return
        }

        val (phrase, delayMs) = utteranceQueue[currentUtteranceIndex]
        currentUtteranceIndex++

        // Store phrase to be displayed when TTS actually starts (in onStart callback)
        pendingPhrase = phrase

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "utterance_$currentUtteranceIndex"
        params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = VOICE_VOLUME.toString()

        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params)

        // Schedule next phrase after this one completes (handled in onDone callback)
        // The delay will be applied there
    }

    /**
     * Called when an utterance finishes
     */
    private fun didFinishSpeaking() {
        if (!isCustomMode) {
            isSpeaking = false
            return
        }

        // Check if there are more phrases to speak
        if (currentUtteranceIndex < utteranceQueue.size) {
            // Get the delay from the PREVIOUS utterance (the one that just finished)
            val delayMs = if (currentUtteranceIndex > 0) {
                utteranceQueue[currentUtteranceIndex - 1].second
            } else {
                0L
            }

            // Wait for the delay, then speak the next phrase
            scope.launch {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                speakNextPhrase()
            }
        } else {
            // All done - meditation completed successfully
            isSpeaking = false
            // KEEP isPlayingMeditation = true so leaf stays green
            // (User can manually toggle it off if desired)
            isCustomMode = false
            utteranceQueue.clear()
            currentUtteranceIndex = 0

            // Set the meditation completion flag for wake-up greeting
            prefs.edit().putBoolean(PREF_MEDITATION_COMPLETED, true).apply()
        }
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
