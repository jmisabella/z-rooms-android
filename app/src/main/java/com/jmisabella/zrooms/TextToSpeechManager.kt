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
    private val customStoryManager: CustomStoryManager? = null,
    private val customPoetryManager: CustomPoetryManager? = null,
    private val storyCollectionManager: StoryCollectionManager? = null
) {
    var isSpeaking by mutableStateOf(false)
        private set

    var contentMode by mutableStateOf(ContentMode.OFF)

    var isLoading by mutableStateOf(false)  // For 3-dot pulsing indicator during transitions
        private set

    // Deprecated - use contentMode instead
    @Deprecated("Use contentMode instead")
    val isPlayingStory: Boolean
        get() = contentMode == ContentMode.STORY

    var ambientVolume by mutableStateOf(1.0f) // 0.0 (silent) to 1.0 (full ambient), default to 100%
        private set

    var currentPhrase by mutableStateOf("")
        private set

    var previousPhrase by mutableStateOf("")
        private set

    var phraseHistory by mutableStateOf(listOf<String>())
        private set

    var hasNewCaptionContent by mutableStateOf(false)

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var utteranceQueue = mutableListOf<Pair<String, Long>>() // (phrase, delay in ms)
    private var currentUtteranceIndex = 0
    private var isCustomMode = false
    private var pendingPhrase: String? = null // Phrase waiting to be displayed when TTS starts
    private var lastPlayedStory: String? = null // Track last story to avoid repeats
    private var lastPlayedPoem: String? = null // Track last poem to avoid repeats
    private val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

    // Sequential chapter playback - now managed by StoryCollectionManager
    private val voiceManager = VoiceManager.getInstance(context)

    // Callback to notify when ambient volume changes
    var onAmbientVolumeChanged: ((Float) -> Unit)? = null

    companion object {
        private const val STORY_PITCH = 1.0f // Natural pitch for all voices
        const val MAX_AMBIENT_VOLUME = 1.0f // Maximum ambient volume
        const val PREF_CONTENT_MODE = "contentMode"
        const val PREF_CONTENT_COMPLETED = "contentCompletedSuccessfully"
        @Deprecated("Use PREF_CONTENT_COMPLETED instead")
        const val PREF_STORY_COMPLETED = "storyCompletedSuccessfully"
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Apply preferred voice and speech parameters
                applyVoiceSettings()
                isInitialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        // Update caption when TTS actually starts speaking
                        pendingPhrase?.let { phrase ->
                            previousPhrase = currentPhrase
                            currentPhrase = phrase
                            pendingPhrase = null

                            // Add to phrase history (avoid duplicates)
                            if (phraseHistory.isEmpty() || phraseHistory.last() != phrase) {
                                phraseHistory = phraseHistory + phrase
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        didFinishSpeaking()
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        contentMode = ContentMode.OFF
                        pendingPhrase = null
                    }
                })
            }
        }
    }

    /**
     * Applies voice settings from VoiceManager
     * Uses dynamic speech rate based on voice quality
     */
    private fun applyVoiceSettings() {
        val preferredVoice = voiceManager.getPreferredVoice()

        if (preferredVoice != null) {
            tts?.setVoice(preferredVoice)
        } else {
            // Fallback to default US English
            tts?.language = Locale.US
        }

        // Apply dynamic speech rate based on voice quality
        val speechRate = voiceManager.getSpeechRateMultiplier(preferredVoice)
        tts?.setSpeechRate(speechRate)

        // Use natural pitch for all voices
        tts?.setPitch(STORY_PITCH)
    }

    /**
     * Refreshes voice settings - call this when user changes voice preferences
     */
    fun refreshVoiceSettings() {
        if (isInitialized) {
            applyVoiceSettings()
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

        // Clear the content completion flag when starting new content
        prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, false).apply()

        isSpeaking = true
        isCustomMode = true

        // Reset phrase history and new content indicator when starting new content
        phraseHistory = emptyList()
        hasNewCaptionContent = false

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
     * Starts speaking a random story from text files in res/raw
     * Always picks a new random story, stopping any existing playback
     */
    fun startSpeakingRandomStory(): String? {
        if (!isInitialized) return null

        // Try to load a random story file
        val storyText = loadRandomStoryFile() ?: return null

        startSpeakingWithPauses(storyText)
        return storyText
    }

    /**
     * Stops speaking immediately
     */
    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
        contentMode = ContentMode.OFF
        utteranceQueue.clear()
        currentUtteranceIndex = 0
        currentPhrase = ""
        previousPhrase = ""
        pendingPhrase = null
        phraseHistory = emptyList()
        hasNewCaptionContent = false

        // Clear the content completion flag when manually stopping
        prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, false).apply()
    }

    /**
     * Splits text into sentences while handling common abbreviations.
     * Returns list of sentences with punctuation preserved.
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val commonAbbreviations = listOf("Dr.", "Mr.", "Mrs.", "Ms.", "vs.", "etc.", "e.g.", "i.e.")

        var currentSentence = StringBuilder()
        var i = 0

        while (i < text.length) {
            val char = text[i]
            currentSentence.append(char)

            // Check for sentence-ending punctuation
            if (char == '.' || char == '!' || char == '?') {
                // Look ahead to check if this is followed by a space/newline (true sentence end)
                val nextChar = if (i + 1 < text.length) text[i + 1] else '\n'
                val isFollowedByWhitespace = nextChar.isWhitespace()

                if (isFollowedByWhitespace) {
                    // Check if this is an abbreviation
                    val currentText = currentSentence.toString().trim()
                    val isAbbreviation = commonAbbreviations.any { abbr ->
                        currentText.endsWith(abbr)
                    }

                    if (!isAbbreviation) {
                        // This is a real sentence boundary
                        val sentence = currentSentence.toString().trim()
                        if (sentence.isNotEmpty()) {
                            sentences.add(sentence)
                        }
                        currentSentence = StringBuilder()
                    }
                }
            }

            i++
        }

        // Add any remaining text as the last sentence
        val remaining = currentSentence.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        return sentences
    }

    /**
     * Automatically adds pauses to text for closed caption sentence breaks and paragraph pauses.
     * Uses (0.5s) markers between sentences within paragraphs.
     * Uses (2s) markers between paragraphs for natural reading flow.
     * Adds <<PARAGRAPH_BREAK>> marker after last sentence of each paragraph.
     */
    private fun addAutomaticPauses(text: String): String {
        val result = StringBuilder()
        val paragraphs = text.split(Regex("\n\\s*\n")) // Split on blank lines

        for ((paragraphIndex, paragraph) in paragraphs.withIndex()) {
            val trimmed = paragraph.trim()

            // Skip empty paragraphs
            if (trimmed.isEmpty()) {
                continue
            }

            // Split paragraph into sentences
            val sentences = splitIntoSentences(trimmed)

            for ((sentenceIndex, sentence) in sentences.withIndex()) {
                if (sentence.isEmpty()) continue

                val isLastSentenceInParagraph = sentenceIndex == sentences.size - 1
                val isLastParagraph = paragraphIndex == paragraphs.size - 1

                // Add the sentence
                result.append(sentence)

                if (isLastSentenceInParagraph) {
                    // Last sentence in paragraph: add paragraph break marker
                    result.append("<<PARAGRAPH_BREAK>>")

                    // Add 2s pause between paragraphs (except after the last one)
                    if (!isLastParagraph) {
                        result.append(" (2s)\n")
                    } else {
                        result.append(" (0.5s)\n")
                    }
                } else {
                    // Not last sentence: add 0.5s pause between sentences
                    result.append(" (0.5s)\n")
                }
            }
        }

        return result.toString()
    }

    /**
     * Extracts phrases and their associated pauses from text
     * Returns list of (phrase, delay in milliseconds)
     * Preserves <<PARAGRAPH_BREAK>> markers as <<PB>> for display purposes
     */
    private fun extractPhrasesWithPauses(text: String): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()

        // Pattern to match pause markers: (3s), (2.5s), (1m), (1.5m), etc.
        val pattern = Regex("""\((\d+(?:\.\d+)?)(s|m)\)""")
        val matches = pattern.findAll(text).toList()

        var lastIndex = 0

        for (match in matches) {
            // Get the phrase before this pause marker
            var phraseBeforePause = text.substring(lastIndex, match.range.first).trim()

            // Preserve paragraph break marker as <<PB>> (shortened for storage)
            phraseBeforePause = phraseBeforePause.replace("<<PARAGRAPH_BREAK>>", "<<PB>>")

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
        var remainingText = text.substring(lastIndex).trim()
        if (remainingText.isNotEmpty()) {
            // Preserve paragraph break marker as <<PB>>
            remainingText = remainingText.replace("<<PARAGRAPH_BREAK>>", "<<PB>>")
            result.add(remainingText to 0L)
        }

        return result
    }

    /**
     * Loads a random story from both preset files and custom storys
     */
    private fun loadRandomStoryFile(): String? {
        // Collect all available storys
        val allStories = mutableListOf<String>()

        // 1. Load all preset story files from res/raw
        // Dynamically discover all preset_story files without hardcoded limit
        var i = 1
        while (true) {
            val resId = context.resources.getIdentifier(
                "preset_story$i",
                "raw",
                context.packageName
            )
            if (resId == 0) {
                // No more preset story files found
                break
            }
            try {
                val text = context.resources.openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
                if (text.isNotEmpty()) {
                    allStories.add(text)
                }
            } catch (e: Exception) {
                // Silently skip story files that can't be read
            }
            i++
        }

        // Custom storys excluded from random selection - only presets play via Leaf button
        // Custom content remains accessible through dedicated list views

        // 2. Check if we have any storys at all
        if (allStories.isEmpty()) {
            return null
        }

        // 4. Pick a random story from all available ones, avoiding the last played one
        val selectedStory = if (allStories.size > 1 && lastPlayedStory != null) {
            // Filter out the last played story and pick from remaining
            val availableStories = allStories.filter { it != lastPlayedStory }
            if (availableStories.isNotEmpty()) {
                availableStories.random()
            } else {
                // Fallback: if filtering resulted in empty list (shouldn't happen), pick any
                allStories.random()
            }
        } else {
            // First time playing or only one story available
            allStories.random()
        }

        // 5. Remember this story to avoid repeating it next time
        lastPlayedStory = selectedStory

        return selectedStory
    }

    /**
     * Loads the current chapter story sequentially (1-indexed files)
     * File naming: preset_story1.txt, preset_story2.txt, etc.
     */
    private fun getSequentialStory(): String? {
        val manager = storyCollectionManager
        if (manager == null) {
            println("❌ getSequentialStory: storyCollectionManager is null")
            return null
        }

        val collection = manager.selectedCollection
        if (collection == null) {
            println("❌ getSequentialStory: selectedCollection is null (collections count: ${manager.collections.size})")
            return null
        }

        val chapterIndex = manager.getChapterIndex(collection.directoryName)
        println("📖 Loading story: ${collection.displayName}, chapter index: $chapterIndex")

        val storyText = manager.getStoryText(collection, chapterIndex)
        if (storyText == null) {
            println("❌ getSequentialStory: getStoryText returned null for ${collection.displayName} chapter $chapterIndex")
        } else {
            println("✅ Successfully loaded story (${storyText.length} chars)")
        }

        return storyText
    }

    /**
     * Loads a random poem from preset files only
     */
    private fun loadRandomPoemFile(): String? {
        val manager = storyCollectionManager ?: return null
        val collection = manager.selectedCollection ?: return null

        val poemText = manager.getRandomPoem(collection)
        if (poemText != null) {
            lastPlayedPoem = poemText
        }
        return poemText
    }

    /**
     * Starts speaking a random poem from preset files only
     */
    fun startSpeakingRandomPoem(): String? {
        if (!isInitialized) return null

        val poemText = loadRandomPoemFile() ?: return null
        startSpeakingWithPauses(poemText)
        return poemText
    }

    /**
     * Starts speaking the current sequential story chapter
     */
    fun startSpeakingSequentialStory(): String? {
        if (!isInitialized) return null
        val storyText = getSequentialStory() ?: return null
        startSpeakingWithPauses(storyText)
        return storyText
    }

    /**
     * Skips to the next chapter with circular navigation
     * Wraps to first chapter when at last chapter
     */
    fun skipToNextChapter(): Boolean {
        val manager = storyCollectionManager ?: return false
        val collection = manager.selectedCollection ?: return false

        val currentIndex = manager.getChapterIndex(collection.directoryName)
        val sortedChapters = collection.sortedChapters

        if (sortedChapters.isEmpty()) return false

        val nextIndex = if (currentIndex < sortedChapters.size - 1) {
            currentIndex + 1
        } else {
            0  // Wrap to first
        }

        manager.setChapterIndex(nextIndex, collection.directoryName)

        if (contentMode == ContentMode.STORY) {
            val preservedMode = contentMode
            startSpeakingSequentialStory()
            contentMode = preservedMode
        }
        return true
    }

    /**
     * Skips to the previous chapter with circular navigation
     * Wraps to last chapter when at first chapter
     */
    fun skipToPreviousChapter(): Boolean {
        val manager = storyCollectionManager ?: return false
        val collection = manager.selectedCollection ?: return false

        val currentIndex = manager.getChapterIndex(collection.directoryName)
        val sortedChapters = collection.sortedChapters

        if (sortedChapters.isEmpty()) return false

        val previousIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            sortedChapters.size - 1  // Wrap to last
        }

        manager.setChapterIndex(previousIndex, collection.directoryName)

        if (contentMode == ContentMode.STORY) {
            val preservedMode = contentMode
            startSpeakingSequentialStory()
            contentMode = preservedMode
        }
        return true
    }

    /**
     * Cycles through content modes: OFF → STORY → POETRY → OFF
     * Includes crossfade loading state when transitioning from STORY → POETRY
     */
    suspend fun cycleContentMode() {
        when (contentMode) {
            ContentMode.OFF -> {
                // OFF → STORY (immediate, green) - use sequential playback
                val storyText = startSpeakingSequentialStory()
                if (storyText != null) {
                    contentMode = ContentMode.STORY
                    saveContentMode()
                } else {
                    // Failed to load story - don't change mode
                    println("⚠️ Failed to start story - collection not loaded or no content available")
                }
            }
            ContentMode.STORY -> {
                // STORY → POETRY (with loading transition)
                isLoading = true
                stopSpeaking()

                // Crossfade delay (matches animation duration)
                delay(500)

                startSpeakingRandomPoem()
                contentMode = ContentMode.POETRY
                saveContentMode()
                isLoading = false
            }
            ContentMode.POETRY -> {
                // POETRY → OFF (immediate)
                stopSpeaking()
                contentMode = ContentMode.OFF
                saveContentMode()
            }
        }
    }

    /**
     * Saves current content mode to SharedPreferences
     */
    private fun saveContentMode() {
        prefs.edit().putString(PREF_CONTENT_MODE, contentMode.name).apply()
    }

    /**
     * Restores content mode from SharedPreferences on app restart
     */
    fun restoreContentMode() {
        val savedMode = prefs.getString(PREF_CONTENT_MODE, ContentMode.OFF.name)
        contentMode = try {
            ContentMode.valueOf(savedMode ?: "OFF")
        } catch (e: IllegalArgumentException) {
            ContentMode.OFF
        }

        // If mode was active, restart playback
        when (contentMode) {
            ContentMode.STORY -> startSpeakingSequentialStory()
            ContentMode.POETRY -> startSpeakingRandomPoem()
            ContentMode.OFF -> { /* Do nothing */ }
        }
    }

    /**
     * Speaks the next phrase in the queue
     */
    private fun speakNextPhrase() {
        if (!isCustomMode || currentUtteranceIndex >= utteranceQueue.size) {
            isSpeaking = false
            previousPhrase = currentPhrase
            currentPhrase = ""
            pendingPhrase = null
            return
        }

        val (phrase, delayMs) = utteranceQueue[currentUtteranceIndex]
        currentUtteranceIndex++

        // Store phrase to be displayed when TTS actually starts (in onStart callback)
        pendingPhrase = phrase

        // Clean the phrase for TTS - remove characters that get spoken literally
        // IMPORTANT: Strip <<PB>> marker before speech (never spoken aloud)
        val cleanedPhrase = phrase
            .replace("<<PB>>", "")
            .replace("-", " ")
            .replace("#", "")
            .replace("*", "")
            .replace("_", "")
            .replace("~", "")

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "utterance_$currentUtteranceIndex"
        params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = VoiceManager.VOICE_VOLUME.toString()

        tts?.speak(cleanedPhrase, TextToSpeech.QUEUE_FLUSH, params)

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
            // All done - content completed successfully
            isSpeaking = false
            // KEEP contentMode as is (STORY or POETRY) so button stays colored
            // (User can manually toggle it off if desired)
            isCustomMode = false
            utteranceQueue.clear()
            currentUtteranceIndex = 0

            // Clear caption text so the closed caption box disappears
            currentPhrase = ""
            previousPhrase = ""
            pendingPhrase = null
            phraseHistory = emptyList()
            hasNewCaptionContent = false

            // Set the content completion flag for wake-up greeting
            prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, true).apply()

            // Auto-advance to next chapter for story mode
            if (contentMode == ContentMode.STORY) {
                val manager = storyCollectionManager
                val collection = manager?.selectedCollection
                if (manager != null && collection != null) {
                    val currentIndex = manager.getChapterIndex(collection.directoryName)
                    if (currentIndex < collection.sortedChapters.size - 1) {
                        manager.setChapterIndex(currentIndex + 1, collection.directoryName)
                        scope.launch {
                            delay(1000)
                            startSpeakingSequentialStory()
                        }
                    }
                }
            }
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
