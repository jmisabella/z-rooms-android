# Z Rooms Android - Change Log

## 2025-12-27 10:45: UX Improvement - Default Ambient Audio Volume Reduced to 80%

### **THE REQUEST**

Change the default ambient audio volume from 100% to 80% to provide a better balance between the meditation voice narration and ambient background audio.

### **THE PROBLEM**

**User Experience Issue:**
At the default 100% ambient volume level, the meditation voice (fixed at 23% volume) was too quiet relative to the ambient background audio. This created a suboptimal listening experience where users had to manually adjust the ambient slider down to hear the meditation guidance clearly.

**Testing Results:**
After testing across all ambient audio track types (white noise, dark ambient, bright ambient, and classical compositions), the user found that 80% ambient volume provided the ideal ratio between voice clarity and ambient atmosphere.

### **THE SOLUTION**

Updated the default ambient volume from 100% (1.0f) to 80% (0.8f) in both TextToSpeechManager and AudioService. This change:
1. Provides better out-of-box experience for new users
2. Ensures meditation voice narration is clearly audible over ambient audio
3. Maintains full user control (slider still allows 0-100% adjustment)
4. Works well across all ambient track styles

**Code Changes:**

**TextToSpeechManager.kt** - Updated default ambient volume:
```kotlin
// Before:
var ambientVolume by mutableStateOf(1.0f) // default to 100%

// After:
var ambientVolume by mutableStateOf(0.8f) // default to 80%
```

**AudioService.kt** - Updated target ambient volume to match:
```kotlin
// Before:
private var targetAmbientVolume: Float = 1.0f // default to 100%

// After:
private var targetAmbientVolume: Float = 0.8f // default to 80%
```

### **FILES MODIFIED**

- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L30) - Changed default `ambientVolume` from 1.0f to 0.8f
- [app/src/main/java/com/jmisabella/zrooms/AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt#L42) - Changed default `targetAmbientVolume` from 1.0f to 0.8f

### **USER IMPACT**

**Positive Changes:**
- Better default experience - voice narration is clearly audible without manual adjustment
- Improved meditation quality with optimal voice-to-ambient ratio
- Tested and verified across all 4 ambient audio styles

**No Breaking Changes:**
- Users can still adjust ambient volume from 0-100% using the slider
- Setting is not persisted, so always resets to the new 80% default on app launch
- Meditation voice volume remains unchanged at 23%

---

## 2024-12-26 16:30: Bug Fix - Voice Settings Not Applied to Meditations

### **THE REQUEST**

User reported that when selecting a different voice in the Voice Settings dialog, the selected voice was not being applied to guided meditations. The meditation would continue using the default system voice instead of the user's chosen voice.

### **THE PROBLEM**

**Root Cause:**
The `VoiceManager.setPreferredVoice()` method was saving the voice selection to preferences but was not enabling the `useEnhancedVoice` flag. The `getPreferredVoice()` method checks if enhanced voice is enabled, and returns `null` (default system voice) when disabled, even if a voice has been selected.

**Impact:**
- Users could select enhanced voices but they would not be used for meditation playback
- Voice previews worked correctly (they used their own TTS instance)
- Actual meditation playback ignored the voice selection and used the default system voice
- User experience was confusing as the settings appeared to save but had no effect

**Code Flow Analysis:**
1. User selects voice in VoiceSettingsView → calls `voiceManager.setPreferredVoice(voice)`
2. `setPreferredVoice()` saves voice name to preferences but doesn't enable enhanced voice
3. When meditation plays → `TextToSpeechManager.applyVoiceSettings()` calls `voiceManager.getPreferredVoice()`
4. `getPreferredVoice()` checks `if (!useEnhancedVoice.value)` and returns `null`
5. TTS engine uses default system voice instead of selected voice

### **THE SOLUTION**

Modified `VoiceManager.setPreferredVoice()` to automatically enable `useEnhancedVoice` when a voice is selected. This ensures that:
1. When user selects a voice, the enhanced voice feature is automatically enabled
2. The selected voice is properly saved to SharedPreferences
3. Both preferences (`PREF_PREFERRED_VOICE_NAME` and `PREF_USE_ENHANCED_VOICE`) are updated atomically in a single transaction

**Code Changes:**
```kotlin
// Before (BUGGY):
fun setPreferredVoice(voice: Voice?) {
    selectedVoice.value = voice
    if (voice != null) {
        prefs.edit().putString(PREF_PREFERRED_VOICE_NAME, voice.name).apply()
    } else {
        prefs.edit().remove(PREF_PREFERRED_VOICE_NAME).apply()
    }
}

// After (FIXED):
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
```

### **FILES CHANGED**

- `app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt` - Fixed `setPreferredVoice()` method
- `BUGS.md` - New file created to track bugs and their resolutions
- `change_log.md` - This entry

### **TESTING**

To verify the fix:
1. Open a room
2. Tap the Voice Settings button (gear icon)
3. Select a different voice from the list (e.g., "Alex" or "Samantha")
4. Close the Voice Settings dialog
5. Tap the meditation button (leaf icon) to play a guided meditation
6. Verify the meditation uses the selected voice (not the default system voice)

### **DEPLOYMENT NOTE**

This fix is critical for Play Store release as it affects a core user-facing feature. Users expect voice selection to work immediately and intuitively.

---

## 2024-12-26 14:00: Debug Logging Removal for Play Store Deployment

### **THE REQUEST**

Remove all debug logging (`println()` statements) from the codebase to prepare for Play Store deployment and prevent unnecessary logging on users' devices.

### **THE PROBLEM**

The app contained 46 active `println()` debug statements across 7 Kotlin files used during development for debugging:
- UI state changes
- Gesture detection
- Alarm and meditation state transitions
- File loading operations
- TTS initialization

**Impact:**
- Unnecessary string allocations and I/O operations on users' devices
- Cluttered logcat output on production builds
- Increased APK size due to debug strings
- Not production-ready for Play Store submission

### **THE SOLUTION**

Systematically removed all 46 `println()` statements across the codebase:

**Files Modified:**
1. **ContentView.kt** - 17 statements removed
   - Alarm selection state changes
   - Sheet state changes
   - Swipe gesture detection
   - Fixed unused parameter warnings

2. **ExpandingView.kt** - 13 statements removed
   - Room entry animations
   - Swipe/tap gesture detection
   - Alarm state changes
   - UI button interactions

3. **AlarmSelectionView.kt** - 10 statements removed
   - Sheet state changes
   - Alarm tile selection
   - Fixed unused parameter warnings

4. **TextToSpeechManager.kt** - 3 statements removed
   - Meditation file loading
   - Replaced with silent error handling

5. **MainActivity.kt** - 1 statement removed
   - onCreate lifecycle logging

6. **CustomMeditationManager.kt** - 1 statement removed
   - Default meditation loading errors

7. **AlarmSelectionContent.kt** - 1 statement removed
   - Alarm tile tap detection

### **BENEFITS**

✅ **Reduced app size** - No debug strings in production build
✅ **Improved performance** - No unnecessary logging operations on users' devices
✅ **Cleaner logs** - Users won't see debug output
✅ **Play Store ready** - Professional production build without debug clutter

All changes preserve the original functionality while removing the debug logging overhead.

---

## 2024-12-26 00:15: Voice Settings UI Simplification

### **THE REQUEST**

The user requested simplification of the Voice Settings interface to make voice selection more straightforward and reduce UI clutter. Specific changes requested:

1. Remove the Enhanced Voice toggle button - all voices should display all the time instead of requiring toggle activation
2. Move the "About Enhanced Voices" section to the top of Voice Settings
3. Change the section title from "About Enhanced Voices" to "About Voices"
4. Remove the last sentence: "Enhanced voiced do not increase app size and are stored in your device's system storage"
5. Make the "About Voices" section scrollable so it doesn't permanently occupy screen space when browsing voices
6. Remove the "Get More Voices" button and simplify the About Voices description

### **THE PROBLEM**

The original Voice Settings interface had several UX issues:

1. **Two-Step Activation**: Users had to toggle Enhanced Voice ON before they could see and select available voices, adding unnecessary friction
2. **Fixed Info Section**: The "About Voices" information card stayed fixed at the top while scrolling, taking up valuable screen real estate and minimizing the number of visible voice options
3. **Redundant Title**: "About Enhanced Voices" was redundant since the context already made it clear these were enhanced voices
4. **Unnecessary Information**: The sentence about app size and system storage was more technical detail than users needed
5. **Get More Voices Button**: The deep-link to TTS settings added complexity without clear benefit, and instructions for downloading voices were unnecessarily detailed for a meditation app

### **THE SOLUTION**

**Implementation Changes:**

1. **Removed Enhanced Voice Toggle** (lines 75-113):
   - Deleted the entire Card component containing the toggle switch
   - Removed the `useEnhancedVoice` variable that was no longer needed
   - Eliminated conditional rendering - voice list now always displays

2. **Repositioned About Section** (lines 79-109):
   - Moved "About Voices" card inside the LazyColumn as the first `item`
   - Changed from fixed position to scrollable item
   - Users can now scroll past it to see more voices

3. **Simplified Title and Content**:
   - Changed title from "ℹ️ About Enhanced Voices" to "ℹ️ About Voices" (line 91)
   - Replaced detailed 3-paragraph description with simple 2-sentence explanation (lines 98-102)
   - Old text: Explained voice management, download sizes (100-500MB), step-by-step settings navigation, and storage details
   - New text: "Voices are provided by your device's Text-to-Speech engine. The voices shown here are currently available on your device."

4. **Removed "Get More Voices" Button**:
   - Deleted entire OutlinedButton component and deep-link logic (~30 lines)
   - Removed unused `context` variable (LocalContext.current)
   - Removed unused Intent imports
   - Rationale: Users can still access TTS settings through device settings if needed, but this complexity doesn't belong in a meditation app's voice selector

5. **Reorganized Layout Structure**:
   - "About Voices" section (scrollable)
   - "Voice Selection" header (scrollable)
   - Voice list items (scrollable)

### **USER EXPERIENCE IMPROVEMENTS**

**Before:**
- Toggle Enhanced Voice ON → Wait for voice list to appear → Scroll through voices with fixed info card taking up space → "Get More Voices" button at bottom
- Limited voice options visible at once due to fixed "About Enhanced Voices" section
- Three-step process to select a voice
- Verbose instructions about downloading voices, file sizes, and navigation paths

**After:**
- Open Voice Settings → Immediately see all available voices
- Scroll past simple "About Voices" card to maximize visible voice options
- Two-step process to select a voice (simpler, more direct)
- Clean, minimal explanation: just what users need to know

**Benefits:**
- More voices visible on screen at once when browsing
- Faster voice selection workflow
- Cleaner, less cluttered interface
- Information available when needed but not intrusive
- Removed unnecessary technical details and complexity
- Focus on meditation experience rather than TTS management

### **CHANGES MADE**

**VoiceSettingsView.kt Structure (Before):**
```
Header
├── Enhanced Voice Toggle Card (always visible)
├── IF Enhanced Voice ON:
│   ├── Voice Selection Header (fixed)
│   ├── LazyColumn:
│   │   ├── Voice List Items
│   │   ├── Get More Voices Button
│   │   └── About Enhanced Voices Card (verbose, 3 paragraphs)
│   ELSE:
│   └── About Enhanced Voices Card (fixed, verbose)
```

**VoiceSettingsView.kt Structure (After):**
```
Header
└── LazyColumn:
    ├── About Voices Card (scrollable, 2 sentences)
    ├── Voice Selection Header (scrollable)
    └── Voice List Items (scrollable)
```

### **FILES MODIFIED**

- [app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt](app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt):
  - Removed Enhanced Voice toggle Card (deleted ~40 lines)
  - Removed `useEnhancedVoice` variable declaration
  - Removed conditional `if (useEnhancedVoice)` branching logic (deleted ~150 lines)
  - Removed "Get More Voices" button and deep-link logic (deleted ~30 lines)
  - Removed unused `context` variable (LocalContext.current)
  - Moved "About Voices" into LazyColumn as first item (lines 80-105)
  - Simplified section title from "About Enhanced Voices" to "About Voices" (line 92)
  - Replaced verbose 3-paragraph description with concise 2-sentence explanation (lines 98-102)
  - Voice list now always displays without toggle requirement

**Text Changes:**

Old "About Enhanced Voices" content:
> "Higher quality voices are managed by your device's Text-to-Speech engine. Many voices come pre-installed on newer devices, while others may require download (100-500MB each).
>
> To manage voices:
> Settings → Accessibility → Text-to-Speech → Speech Services by Google → Settings icon → Voice selection
>
> Enhanced voices do not increase app size and are stored in your device's system storage."

New "About Voices" content:
> "Voices are provided by your device's Text-to-Speech engine. The voices shown here are currently available on your device."

**Code Reduction:** ~220 lines removed, ~5 lines added = ~215 net line reduction

**Total Lines:** 159 lines (down from ~370 lines)

## 2025-12-25 23:27: Enhanced Opening Phrase Variety in Preset Meditations

### **THE REQUEST**

The user noticed that an excessive number of preset meditations began with the exact same literal phrase: "Before we begin, consider this." This repetitive opening created a monotonous user experience for regular meditation users. The request was to introduce significant variety in the opening phrases while maintaining the overall contemplative meaning and tone.

**Initial Analysis:**
- 27 out of 36 meditation files used "Before we begin,"
- 13 of those used the generic "consider this" after it
- This lack of variety made meditations feel formulaic and less engaging

### **THE SOLUTION**

**Implementation Strategy:**
Developed a diverse collection of 20+ opening phrase variations across 5 categories, distributed thoughtfully across all meditation files based on their content and sources. The approach prioritized attribution-specific phrases for meditations with named sources (Marcus Aurelius, Tao Te Ching, etc.) while using varied invitations and tone-setters for original content.

**Opening Phrase Categories Created:**

**Category 1: Invitations to Reflect**
- "A thought to hold"
- "Consider these words"
- "Reflect on this"
- "Let's begin with this insight"
- "Here's a thought to carry with us"
- "A moment to contemplate"
- "Something to ponder"
- "Something to reflect upon"
- "Here's a reflection"

**Category 2: Setting the Tone**
- "To set our intention"
- "As we prepare"
- "To ground this practice"
- "To guide our journey today"
- "Let us settle in with"
- "We begin with"

**Category 3: Attribution-Specific (for meditations with sources)**
- "In the words of William Wordsworth, from I Wandered Lonely As A Cloud"
- "Marcus Aurelius reminds us, from his Meditations"
- "Wisdom from Ralph Waldo Emerson's Self-Reliance"
- "From Lao Tzu's Tao Te Ching, these words"
- "The Bhagavad Gita teaches us"
- "An ancient teaching from the Tao Te Ching"
- "Wisdom from the Dhammapada"
- "Ancient Buddhist wisdom teaches"
- "From the Upanishads, this teaching"

**Category 4: Gentle Invitation**
- "Let these words guide us"
- "May we hold this truth"
- "A reminder for our practice"
- "Let's begin with this thought"

**Category 5: Direct Entry**
- One meditation (preset_meditation4.txt) starts directly with instructions, no preamble

**Preserved "Before we begin" instances (2-3 total, as requested):**
- "A reflection before we begin" (meditation 10)
- "Before we begin, from the Serenity Prayer" (meditation 25)
- "Before we begin, from an old Zen saying" (meditation 28)

### **CHANGES MADE**

Updated 27 out of 36 meditation files with varied opening phrases:

**Meditations with Attribution-Specific Phrases:**
- preset_meditation1.txt: "In the words of William Wordsworth..."
- preset_meditation2.txt: "Marcus Aurelius reminds us..."
- preset_meditation6.txt: "Wisdom from Ralph Waldo Emerson's Self-Reliance"
- preset_meditation8.txt: "From Lao Tzu's Tao Te Ching, these words"
- preset_meditation11.txt: "The Bhagavad Gita teaches us"
- preset_meditation15.txt: "An ancient teaching from the Tao Te Ching"
- preset_meditation20.txt: "Wisdom from the Dhammapada"
- preset_meditation21.txt: "Ancient Buddhist wisdom teaches"
- preset_meditation30.txt: "From the Upanishads, this teaching"

**Meditations with Reflection & Invitation Phrases:**
- preset_meditation3.txt: "Here's a reflection"
- preset_meditation7.txt: "Let us settle in with"
- preset_meditation12.txt: "To guide our journey today"
- preset_meditation13.txt: "A thought to hold"
- preset_meditation14.txt: "Let's begin with this insight"
- preset_meditation16.txt: "Something to ponder"
- preset_meditation17.txt: "To set our intention"
- preset_meditation18.txt: "Reflect on this"
- preset_meditation19.txt: "Here's a thought to carry with us"
- preset_meditation22.txt: "Something to reflect upon"
- preset_meditation24.txt: "A moment to contemplate"
- preset_meditation26.txt: "As we prepare"
- preset_meditation29.txt: "Consider these words"
- preset_meditation31.txt: "To ground this practice"
- preset_meditation32.txt: "Let these words guide us"
- preset_meditation33.txt: "A reminder for our practice"
- preset_meditation34.txt: "May we hold this truth"
- preset_meditation35.txt: "We begin with"

### **DISTRIBUTION & VARIETY ACHIEVED**

**Before:**
- 27 files using "Before we begin,"
- 13 files with "Before we begin, consider this"
- Extremely repetitive, formulaic feel

**After:**
- Only 2-3 files retain "Before we begin" (10, 25, 28)
- 20+ unique opening phrases across all categories
- Variety distributed evenly based on meditation content and sources
- Each meditation feels more unique and thoughtfully crafted

### **FILES MODIFIED**

All meditation files in `zz-time/Meditations/`:
- preset_meditation1.txt through preset_meditation35.txt
- Total: 27 files updated with new opening phrases
- 3 files retained "Before we begin" for variety
- Multiple files already had "Let's begin with this thought" variations (kept for continuity)


## 2025-12-25 15:00 EST

**Feature Enhancement:** Prevent Consecutive Meditation Repeats - Ensures variety in meditation selection

**User Experience:** When users toggle the leaf button on to play a meditation, then toggle it off to stop, and toggle it back on again, the app now guarantees that the second meditation will be different from the first. This prevents the jarring experience of hearing the exact same meditation twice in a row when toggling the leaf button multiple times.

**Implementation Details:**

Added meditation history tracking to prevent immediate repeats:

1. **Last Played Tracking:**
   - New variable `lastPlayedMeditation: String?` stores the full text of the most recently played meditation
   - Updated when meditation is selected in `loadRandomMeditationFile()`
   - Persists for the app session (not saved to disk)

2. **Smart Random Selection:**
   - When 2+ meditations available AND a previous meditation exists:
     - Filters out `lastPlayedMeditation` from available pool
     - Selects randomly from remaining meditations
   - When only 1 meditation available:
     - Plays that single meditation (no choice)
   - First time playing (no previous meditation):
     - Selects randomly from all available meditations

3. **Selection Logic:**
```kotlin
val selectedMeditation = if (allMeditations.size > 1 && lastPlayedMeditation != null) {
    // Filter out the last played meditation and pick from remaining
    val availableMeditations = allMeditations.filter { it != lastPlayedMeditation }
    availableMeditations.random()
} else {
    // First time playing or only one meditation available
    allMeditations.random()
}
lastPlayedMeditation = selectedMeditation
```

**User Scenarios:**

*Scenario 1: Typical Toggle On/Off/On (Multiple Meditations Available)*
- User toggles leaf ON → Meditation #12 plays
- User toggles leaf OFF → Meditation stops
- User toggles leaf ON → Meditation #27 plays (guaranteed NOT #12)
- User toggles leaf OFF → Meditation stops
- User toggles leaf ON → Any meditation EXCEPT #27 plays

*Scenario 2: Only One Meditation Available*
- User has deleted all presets except one, no custom meditations
- User toggles leaf ON → Meditation #1 plays
- User toggles leaf OFF → Meditation stops
- User toggles leaf ON → Meditation #1 plays (only choice available)

*Scenario 3: With 70 Total Meditations (35 presets + 35 custom)*
- Each toggle ensures variety: 69 different options each time
- Prevents repetitive experience even with frequent toggling
- Last played meditation only persists during app session

**Benefits:**
- Better user experience with guaranteed variety
- Prevents frustration from hearing same meditation immediately after stopping it
- Works seamlessly with both preset and custom meditations
- No impact on first-time meditation selection
- Minimal memory overhead (stores single meditation text)

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Added `lastPlayedMeditation` tracking (line 46), updated `loadRandomMeditationFile()` with smart selection logic (lines 324-342)

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
