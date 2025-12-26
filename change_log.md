# Z Rooms Android - Change Log

## 2025-12-25 14:30 EST

**Bug Fix:** Voice Volume Consistency - Unified TTS voice volume across all playback instances

**Problem:** The voice preview feature in VoiceSettingsView was playing at full volume (1.0), which was significantly louder than the meditation narration volume (0.23f). This created a jarring experience where users would hear previews at one volume level but meditations at a much quieter level. Additionally, the VOICE_VOLUME constant was duplicated in multiple files (TextToSpeechManager and referenced in AudioService), violating DRY principles and creating maintenance risk.

**Solution:** Consolidated voice volume management by:
1. Moving the VOICE_VOLUME constant to VoiceManager as the single source of truth
2. Adding volume parameter to preview TTS in VoiceManager.previewVoice()
3. Updating all TTS instances to reference VoiceManager.VOICE_VOLUME

**Implementation Details:**

All three TTS voice instances now use the same volume (0.23f):
- **Meditation narration** (TextToSpeechManager.speakNextPhrase())
- **Voice preview** (VoiceManager.previewVoice())
- **Wake-up greeting** (AudioService.playWakeUpGreeting())

**Code Changes:**

```kotlin
// VoiceManager.kt - Single source of truth for voice volume
companion object {
    // Voice volume (shared across meditation playback and preview)
    const val VOICE_VOLUME = 0.23f
}

// Preview now uses same volume as meditations
fun previewVoice(voice: Voice, text: String, onComplete: (() -> Unit)? = null) {
    val params = HashMap<String, String>()
    params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "preview"
    params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = VOICE_VOLUME.toString()
    // ...
}
```

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt) - Added VOICE_VOLUME constant (line 44), added volume parameter to preview TTS (line 270)
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Removed duplicate VOICE_VOLUME constant, updated reference to VoiceManager.VOICE_VOLUME (line 348)
- [app/src/main/java/com/jmisabella/zrooms/AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt) - Updated wake-up greeting to use VoiceManager.VOICE_VOLUME (line 449)

**User Impact:**
- Voice previews now play at the same comfortable volume as meditations (0.23f instead of 1.0)
- Consistent audio experience across all TTS features
- No more jarring volume differences when testing voices

## 2025-12-25

**Feature:** Enhanced Voice Quality - Optional high-quality TTS voices for meditation narration

**User Experience:** Users can now optionally select higher-quality TTS voices for meditation narration while maintaining the default "artificial" voice aesthetic as the default. A new gear icon (settings button) appears in the meditation view (ExpandingView), positioned as the leftmost button in the bottom row. Tapping this icon opens voice settings where users can toggle "Enhanced Voice" on/off and select from available high-quality voices. Each voice can be previewed with a sample meditation phrase before selection. The app maintains 100% offline functionality - only voices that don't require network connectivity are shown.

**Implementation Details:**

The enhanced voice quality feature consists of three main components:

1. **Voice Management System** (VoiceManager.kt - NEW, 335 lines):
   - Singleton pattern managing TTS voice discovery, selection, and configuration
   - Discovers all available offline English voices and filters/sorts by quality
   - Quality detection: Maps Android Voice.QUALITY_* constants to user-friendly labels
   - Persistent voice preferences via SharedPreferences (keys: `use_enhanced_voice`, `preferred_voice_name`)
   - Dynamic speech rate calculation based on voice quality:
     - Default voices (QUALITY_NORMAL): 0.8x multiplier (slower for robotic voices)
     - Enhanced voices (QUALITY_HIGH): 1.0x multiplier (natural speed)
     - Premium voices (QUALITY_VERY_HIGH): 1.0x multiplier (natural speed)
   - Natural pitch (1.0) for ALL voices (removed artificial pitch lowering)
   - Voice name mapping: Converts cryptic Google TTS codes to friendly names
     - Female voices: sfg→Samantha, iob→Emily, iog→Victoria
     - Male voices: tpf→Alex, iom→Daniel, tpd→Michael, tpc→James
     - Fallback to generic names (Voice A, Voice B, etc.) for unknown codes
   - Separate preview TTS instance to avoid interfering with meditation playback
   - Preview management: `stopPreview()` prevents audio looping and allows rapid voice comparison
   - `getPreferredVoice()` fallback logic: User selection → Any high-quality voice → Default system voice

2. **Voice Settings UI** (VoiceSettingsView.kt - NEW, 370 lines):
   - Jetpack Compose full-screen settings interface
   - Enhanced Voice toggle (OFF by default) - preserves default artificial aesthetic
   - Scrollable LazyColumn voice list (only visible when Enhanced Voice enabled)
   - VoiceListItem components showing:
     - Friendly voice name (no redundant quality label - context already clear)
     - Locale display (e.g., "English (United States)")
     - Download status indicator for voices requiring download
     - Preview button (play/stop icon) with immediate switching
     - Selected indicator (checkmark icon)
   - Preview functionality: Sample text "Welcome to your meditation practice. Take a deep breath and relax."
   - Preview state management: Clicking new preview immediately stops previous one
   - "Get More Voices" button: Deep-links to Android TTS settings for voice management
   - Info section (scrollable, at bottom of list when Enhanced Voice ON):
     - Explains voices are managed by device's TTS engine
     - Notes many voices come pre-installed, others require download (100-500MB each)
     - Provides path: Settings → Accessibility → Text-to-Speech → Speech Services by Google → Settings icon → Voice selection
     - Clarifies enhanced voices stored in device system storage, not app
   - Close button returns to meditation view and refreshes voice settings

3. **Integration Changes**:

   **TextToSpeechManager.kt** (MODIFIED):
   - Removed hardcoded speech rate/pitch constants
   - Added VoiceManager instance initialization
   - Added `applyVoiceSettings()`: Applies preferred voice and dynamic speech rate
   - Modified TTS initialization to call `applyVoiceSettings()`
   - Added `refreshVoiceSettings()`: Callable when user changes voice preferences
   - Uses `MEDITATION_PITCH = 1.0f` constant (natural pitch for all voices)
   - Voice selection priority: Enhanced voice (if enabled) → Default US English locale

   **ExpandingView.kt** (MODIFIED):
   - Added gear icon (Settings) as leftmost button in bottom row
   - Button order: gear → quote (custom meditation) → clock (alarm timer) → leaf (meditation toggle)
   - Added `showVoiceSettings` state variable for sheet presentation
   - VoiceSettingsView presented as full-screen overlay when gear icon tapped
   - Calls `ttsManager.refreshVoiceSettings()` on dismiss to apply voice changes

**Voice Quality Configuration:**

```kotlin
// VoiceManager.kt - Dynamic speech rate based on voice quality
fun getSpeechRateMultiplier(voice: Voice?): Float {
    if (voice == null || !useEnhancedVoice.value) {
        return 0.8f  // DEFAULT_VOICE_RATE
    }

    // Enhanced and high-quality voices sound better at normal speed
    return if (voice.quality >= Voice.QUALITY_HIGH) {
        1.0f  // ENHANCED_VOICE_RATE
    } else {
        0.8f  // DEFAULT_VOICE_RATE
    }
}
```

**Voice Discovery and Filtering:**

```kotlin
// VoiceManager.kt - Discovers offline English voices only
private fun discoverVoices() {
    val voices = tts?.voices?.filter { voice ->
        voice.locale.language == "en" && !voice.isNetworkConnectionRequired
    }?.sortedWith(compareByDescending<Voice> {
        it.quality  // Sort by quality (high to low)
    }.thenBy {
        if (it.locale == Locale.US) 0 else 1  // Then US locale first
    }) ?: emptyList()

    availableVoices.value = voices
}
```

**Friendly Voice Names (Android-Specific):**

Android voice names are cryptic (e.g., "en-us-x-tpf-local"). VoiceManager maps Google TTS voice codes to iOS-like friendly names:

```kotlin
// Extract voice code from name like "en-us-x-tpf-local"
val voiceCode = when {
    name.contains("-x-") -> {
        name.substringAfter("-x-").substringBefore("-").substringBefore("#")
    }
    else -> ""
}

// Map to friendly names based on actual voice characteristics
return when (voiceCode) {
    "sfg" -> "Samantha"      // Female
    "iob" -> "Emily"         // Female
    "iog" -> "Victoria"      // Female
    "tpf" -> "Alex"          // Male
    "iom" -> "Daniel"        // Male
    "tpd" -> "Michael"       // Male
    "tpc" -> "James"         // Male
    else -> "Voice A/B/C..."  // Fallback by position
}
```

**User Scenarios:**

*Scenario 1: Default User (No Changes)*
- Enhanced Voice toggle remains OFF (default)
- Meditations use default system voice at 0.8x speed, 1.0 pitch
- Exactly same experience as before this feature was added
- No behavior change

*Scenario 2: Enhanced Voice Discovery*
- User taps gear icon in meditation view
- Opens voice settings
- Toggles "Enhanced Voice" ON
- Scrollable list of voices appears with friendly names
- Taps preview button on "Samantha" → Hears sample at 1.0x speed
- Taps preview button on "Alex" → Previous preview stops, new one starts
- Selects "Samantha" (checkmark appears)
- Closes settings
- Next meditation uses Samantha voice at natural speed (1.0x)
- Preference persists across app restarts

*Scenario 3: Voice Requires Download*
- User selects voice with "Needs Download" indicator
- Voice won't work until downloaded
- Taps "Get More Voices" button
- Deep-linked to Android TTS settings
- Downloads voice via Google Text-to-Speech app
- Returns to z rooms app
- Selected voice now available for meditations

*Scenario 4: Enhanced Voice Unavailable (Fallback)*
- User previously selected enhanced voice "Victoria"
- Voice gets deleted from system settings
- App fallback priority:
  1. Try to use Victoria → Not available
  2. Try any QUALITY_HIGH voice for en-US → If available, use it
  3. Fall back to default system voice → Always available
- Meditation plays successfully with fallback voice

**Android-Specific Implementation Notes:**

Unlike iOS where voices download automatically on first use, Android requires manual voice download through the Google Text-to-Speech app settings. The implementation handles this with:
- Clear "Needs Download" indicators on unavailable voices
- "Get More Voices" button with deep-link to `com.android.settings.TTS_SETTINGS`
- Info section explaining download process step-by-step
- Fallback logic when selected voice unavailable

**Key Technical Decisions:**

1. **Offline-Only Voices**: Only shows voices with `isNetworkConnectionRequired == false`
2. **Singleton Pattern**: VoiceManager ensures single TTS instance for voice discovery
3. **Separate Preview TTS**: Prevents interference with meditation playback
4. **Observable State**: Uses Compose `mutableStateOf` for reactive UI updates
5. **SharedPreferences Persistence**: Survives app restarts, device reboots
6. **Natural Pitch (1.0)**: Removed artificial pitch lowering (0.58 in old code) for all voices
7. **Dynamic Speech Rate**: Quality-based adjustment for optimal listening experience
8. **Immediate Preview Switching**: Better UX for voice comparison

**Files Created:**
- [app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt) - Voice management singleton (335 lines)
- [app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt](app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt) - Compose UI for voice settings (370 lines)

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Integrated VoiceManager, dynamic voice selection and speech rate
- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) - Added gear icon button and VoiceSettingsView sheet presentation

**Total Code Changes:** ~705 lines new, ~30 lines modified = ~735 lines total

## 2025-12-21

**Feature:** Wake-Up Greeting - Personalized audio greetings for users who complete meditations before their alarm

**User Experience:** When a user completes a guided meditation the night before and wakes to a non-SILENCE alarm, the app now speaks a brief, randomized greeting phrase approximately 5 seconds after the alarm begins playing. This creates a gentle, personalized wake-up experience that acknowledges the user's meditation practice.

**Implementation Details:**

The wake-up greeting feature consists of three main components:

1. **Meditation Completion Tracking** (TextToSpeechManager.kt):
   - Added `PREF_MEDITATION_COMPLETED` SharedPreferences key to track successful meditation completion
   - When meditation starts: Flag is cleared to ensure fresh state
   - When meditation completes fully: Flag is set to true AND `isPlayingMeditation` state remains true (keeping leaf button green)
   - When meditation is manually stopped: Flag is cleared
   - This allows the system to distinguish between completed vs. interrupted meditations

2. **Greeting TTS Engine** (AudioService.kt):
   - Added dedicated `greetingTts` TextToSpeech instance separate from meditation TTS
   - Initialized with same voice settings as meditations (speech rate: 0.6f, pitch: 0.58f)
   - Five randomized greeting phrases: "Welcome back", "Greetings", "Here we are", "Returning to awareness", "Welcome back to this space"
   - Intentionally avoids time-specific words like "morning" (user might wake from afternoon nap)

3. **Alarm Integration** (AudioService.kt):
   - Modified `startAlarm()` to check meditation completion flag when alarm triggers
   - If alarm is SILENCE (selectedAlarmIndex is null or -1): No greeting plays, ambient fades to nothing
   - If alarm is non-SILENCE AND meditation was completed: Greeting is scheduled
   - `scheduleWakeUpGreeting()` waits 5 seconds after alarm audio starts
   - `playWakeUpGreeting()` speaks random phrase and clears completion flag
   - Greeting plays only ONCE (does not repeat with alarm loop)

**Bug Fix - Leaf Button State:**

Fixed a significant UX issue where the leaf button would turn grey (toggle off) when a meditation completed successfully, making it impossible to distinguish between a completed meditation and one that was never started.

**Previous Behavior:**
- Meditation completes → `isPlayingMeditation` set to false → Leaf turns grey
- User has no visual indication that meditation completed successfully

**New Behavior:**
- Meditation completes → `isPlayingMeditation` stays true → Leaf stays green
- User can see meditation completed successfully (green leaf)
- User can manually toggle leaf off by tapping it if desired
- Wake-up greeting system can detect successful completion

**Trigger Conditions (ALL must be met for greeting to play):**
1. Alarm/wake time is reached
2. Non-SILENCE waking room selected (alarm sound plays)
3. Guided meditation completed successfully the night before (leaf is green)

**Exclusions:**
- Does NOT play if SILENCE is selected (no alarm sound means no greeting)
- Does NOT play if meditation was interrupted or manually stopped
- Does NOT play if no meditation was started

**User Scenarios:**

*Scenario 1: Typical Weekday Morning (Greeting Plays)*
- User toggles meditation on before sleep
- Meditation plays to completion → Leaf stays green
- Alarm triggers with classical music (rooms 31-35)
- ~5 seconds later: Random greeting phrase spoken
- Flag cleared, ready for next night

*Scenario 2: Weekend Morning with SILENCE (No Greeting)*
- User toggles meditation on before sleep
- Meditation completes → Leaf stays green
- Alarm triggers with SILENCE selected
- Ambient audio fades to nothing
- No greeting plays (SILENCE means user doesn't want alarm sound)

*Scenario 3: No Meditation (No Greeting)*
- User sleeps without toggling meditation
- Alarm triggers with classical music
- No greeting plays (meditation wasn't completed)

*Scenario 4: Interrupted Meditation (No Greeting)*
- User toggles meditation on but manually stops it mid-session
- Alarm triggers with classical music
- No greeting plays (meditation wasn't completed successfully)

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Added SharedPreferences tracking for meditation completion state, fixed leaf button to stay green after completion
- [app/src/main/java/com/jmisabella/zrooms/AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt) - Added wake-up greeting TTS engine, scheduling logic, and alarm integration

## 2025-12-21
- Added better variation to the preset meditations.

## 2025-12-21

**Problem:** Two meditation playback issues were discovered after the previous fix:
1. **Resume Instead of New Selection**: When toggling the leaf button off and back on, the app would sometimes resume the previous meditation instead of selecting a new random one, reducing variety and creating a confusing user experience.
2. **Caption/Audio Delay on First Play**: When first toggling meditation on, there was a significant delay between when the closed caption text appeared and when the TTS audio actually started playing, creating an awkward pause.

**Root Cause:**
1. The `startSpeakingWithPauses()` function checked `if (isSpeaking)` and would exit early, which could cause timing issues where the old meditation state wasn't fully cleared before starting a new one. The `startSpeakingRandomMeditation()` also checked `isSpeaking`, preventing a fresh meditation from being selected.
2. The caption text (`currentPhrase`) was being set immediately when calling `tts?.speak()`, but the actual TTS audio had a processing delay before it started, causing the caption to appear well before the audio began.

**Solution:**
1. Modified `startSpeakingWithPauses()` to always call `stopSpeaking()` first, ensuring a clean state before starting any new meditation. Removed the `isSpeaking` check from `startSpeakingRandomMeditation()` so it always selects a new random meditation.
2. Introduced a `pendingPhrase` variable that stores the phrase text, and only updates `currentPhrase` (which controls the caption display) when the TTS engine's `onStart()` callback is triggered, ensuring perfect synchronization between caption display and audio playback.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Fixed meditation restart logic and synchronized caption display with TTS audio start

## 2025-12-20

**Problem:** Random meditation selection was only using the first 10 preset meditations (preset_meditation1 through preset_meditation10) and completely ignoring preset meditations 11-35. This significantly reduced meditation variety and meant users were missing out on 25 of the 35 available preset meditations. This bug was discovered in the iOS version of the app and was confirmed to exist in the Android version as well.

**Root Cause:** In the `loadRandomMeditationFile()` function within TextToSpeechManager.kt, the loop that loads preset meditation files was incorrectly limited to `for (i in 1..10)` instead of iterating through all 35 preset meditation files.

**Solution:** Replaced the hardcoded loop range (`1..10`, later `1..35`) with dynamic discovery logic that automatically finds all available preset meditation files without any upper limit. The function now uses a while loop that continues checking for `preset_meditation$i` files until it finds a gap, ensuring all preset meditations are included regardless of how many exist. This future-proofs the code so that adding new preset meditations (e.g., preset_meditation36 through preset_meditation40) will automatically work without any code changes. Custom meditations were already being loaded correctly via the `forEach` loop over all custom meditation entries.

**Content Update:** All 35 preset meditation files have been updated to incorporate more breathwork guidance, enhancing the meditation experience with structured breathing exercises integrated throughout the guided sessions.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Updated loop range to include all 35 preset meditations
- All 35 preset meditation text files (preset_meditation1.txt through preset_meditation35.txt) - Enhanced with additional breathwork guidance

## 2025-12-13 16:30 EST

**Problem:** After exiting from ExpandingView (room view) back to ContentView, the bottom row and half of the second-to-last row of room tiles incorrectly displayed as rectangles instead of squares in portrait mode. Additionally, on first app launch in portrait mode followed by rotation to landscape mode, the tiles appeared oversized and didn't fit on the screen properly. This regression was introduced by the recent change that added `android:configChanges="keyboardHidden|orientation|screenSize"` to prevent Activity recreation during rotation.

**Root Cause:** The `android:configChanges` attribute prevents the Activity from being destroyed and recreated during orientation changes. While this successfully preserved meditation playback state (as intended), it created an unintended side effect: Compose's `BoxWithConstraints` component in ContentView was not automatically recomposing when orientation changed. This caused the aspect ratio calculations (`aspect = itemW.value / itemH.value`) to retain stale dimension values from the previous orientation, resulting in incorrectly sized tiles.

**Solution:** Wrapped the `LazyVerticalGrid` component inside a `androidx.compose.runtime.key(configuration.orientation)` block. This forces Compose to completely recompose the grid layout whenever the device orientation changes, ensuring that BoxWithConstraints recalculates dimensions and the aspect ratio is always computed with current screen dimensions. The fix maintains the benefit of preserving meditation state during rotation while ensuring the tile grid layout correctly adapts to orientation changes.

**Link to Previous Change:** This issue was a direct regression from the 2025-12-13 13:03 EST change which added `android:configChanges="keyboardHidden|orientation|screenSize"` to AndroidManifest.xml. That change successfully prevented meditation interruption during rotation but inadvertently broke the responsive layout behavior of ContentView's tile grid.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/ContentView.kt](app/src/main/java/com/jmisabella/zrooms/ContentView.kt) - Added orientation-keyed recomposition wrapper around LazyVerticalGrid

## 2025-12-13 15:00 EST

**Problem:** In portrait mode, the closed captioning text appeared too low on the screen and overlapped with the device's navigation buttons (back, home, recent apps), making the captions difficult to read. This issue did not occur in landscape mode.

**Solution:** Added orientation-aware padding using LocalConfiguration to detect device orientation. In portrait mode, 48dp of bottom padding is applied to lift the closed captioning above the navigation buttons. In landscape mode, no additional padding is applied, keeping the gradient flush with the screen edge. This provides optimal readability in portrait mode while maintaining the polished appearance in landscape mode.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) - Added orientation detection and conditional bottom padding

## 2025-12-13 14:45 EST

**Problem:** In landscape mode, the closed captioning gradient overlay had a visible gap between the bottom of the gradient and the bottom edge of the screen, making the app appear unpolished. This gap was not present in portrait mode, creating an inconsistent visual experience.

**Solution:** Restructured the padding in MeditationTextDisplay component to apply padding only to the text content Column (24dp bottom) instead of the outer gradient Box (which previously had 96dp bottom padding). Also removed the additional 40dp bottom padding that was being applied in ExpandingView. This allows the gradient to extend fully to the screen edge in both orientations while maintaining appropriate spacing for the text content.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/MeditationTextDisplay.kt](app/src/main/java/com/jmisabella/zrooms/MeditationTextDisplay.kt) - Moved padding from Box to Column for proper gradient extension
- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) - Removed bottom padding from MeditationTextDisplay modifier

## 2025-12-13 13:03 EST

**Problem:** When a guided meditation was playing and the device was rotated from portrait to landscape (or vice versa), the Leaf button would become untoggled and the guided meditation speech along with closed captioning would stop playing, while the ambient audio continued. This created an inconsistent user experience where rotation interrupted the meditation session.

**Solution:** Added `android:configChanges="keyboardHidden|orientation|screenSize"` attribute to the MainActivity declaration in AndroidManifest.xml. This configuration change handling prevents the Activity from being destroyed and recreated during rotation, preserving the meditation playback state. Now when users rotate their device during a meditation session, the guided meditation and closed captions continue playing seamlessly, just like the ambient audio does.

**Files Modified:**
- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) - Added configChanges attribute to MainActivity
