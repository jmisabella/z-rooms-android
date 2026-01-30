# Z Rooms Android - Change Log

## 2026-01-30: Enhancement - Voice Accept-List Filtering

### **ENHANCEMENT**

Implemented curated voice filtering for enhanced text-to-speech, restricting voice options to those appropriate for dark sci-fi story narration.

**Previous Behavior:**
- Voice settings showed all available English voices on device (often 50+ voices)
- Included many overly cheerful, novelty, or inappropriate voices for dark sci-fi content
- No locale prioritization or quality-based filtering
- Users had to manually identify suitable voices from the full list

**New Behavior:**
- Voice settings show only curated voices (typically 8-15 voices)
- Filters to specific locales: British English (priority 1), Australian English, Indian English, and American English (priority 4)
- Automatically prioritizes British English voices for their serious, narrative-appropriate tone
- Excludes network-required voices (maintains 100% offline functionality)
- Excludes low-quality voices
- Provides consistent UX matching iOS implementation

**Accept-List Criteria:**
- **Locales:** en-GB, en-AU, en-IN, en-US only
- **Quality:** Excludes QUALITY_LOW voices
- **Offline:** Excludes network-required voices
- **Automatic selection priority:** British > Australian > Indian > American

**Technical Details:**
Voice filtering now uses accept-list approach rather than simple exclusion. The `discoverVoices()` method filters voices through `isVoiceAccepted()` which validates locale against accept-list, quality level, and offline availability. The `getPreferredVoice()` hierarchy prioritizes user selection first, then locale-based hierarchy (en-GB → en-AU → en-IN → en-US), then previously saved voice for backward compatibility, then random from accepted list, with system default as final fallback. Removed special "Daniel voice" logic in favor of locale-based prioritization.

**Files Modified:**
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):1-7 - Added android.util.Log import for debugging
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):48-63 - Added accept-list constants (ACCEPTED_LOCALES, LOCALE_PRIORITY_ORDER)
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):91-105 - Added isVoiceAccepted() filter method validating locale, quality, and offline requirements
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):107-131 - Updated discoverVoices() to use accept-list filtering with locale priority sorting
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):138-194 - Replaced getPreferredVoice() with new 5-step hierarchy, removed Daniel voice special case
- [VoiceManager.kt](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt):196-213 - Added getVoiceFromHierarchy() helper method for locale-based voice selection
- [VoiceSettingsView.kt](app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt):139-140 - Updated preview text to dark sci-fi sample ("The signal decayed into static...")
- [VoiceSettingsView.kt](app/src/main/java/com/jmisabella/zrooms/VoiceSettingsView.kt):97 - Updated info section to clarify voice curation and filtering
- [CONTEXT.md](CONTEXT.md):7-9 - Updated voice options documentation to reflect curated system TTS voices (not robotic altered voices)
- [CONTEXT.md](CONTEXT.md):18-25 - Added voice filtering section documenting accept-list criteria and prioritization

---

## 2026-01-18: Enhancement - Immediate Story Switching

### **ENHANCEMENT**

Improved story collection switching to provide immediate feedback when selecting a different story while Story mode is active.

**Previous Behavior:**
- When switching from one story collection to another (e.g., "Signal Decay" to "The Eighteen Paradox") while Story mode was active, the TTS would change to the new story but the UI would break:
  - Leaf button became untoggled (turned gray)
  - Closed caption box disappeared
  - User had to click Leaf button again to restore Story mode and captions
  - New story would restart from the beginning

**New Behavior:**
- When switching stories while Story mode is active:
  - New story begins playing immediately
  - Leaf button remains toggled (green)
  - Closed caption box stays visible with new story text
  - Seamless transition without requiring user to re-enable Story mode

**Technical Details:**
The `startSpeakingWithPauses()` function internally calls `stopSpeaking()`, which sets `contentMode = ContentMode.OFF`. The fix preserves the content mode before restarting playback and immediately restores it after.

**Files Modified:**
- [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):915-922 - Enhanced `onSelectCollection` callback to preserve and restore STORY mode when switching collections during active playback

---

## 2026-01-18: Bug Fix - Story Mode Race Condition & Collection Fallback

### **BUG FIX**

Fixed critical bug where the Leaf button (Story mode toggle) became unresponsive after adding new story collections or when saved collection IDs became invalid.

**Root Causes:**
1. **Race Condition:** Collections were loading asynchronously via `LaunchedEffect`, but the Leaf button could be clicked before loading completed, causing `selectedCollection` to be null.
2. **Missing Fallback Logic:** If the saved collection ID in SharedPreferences didn't match any loaded collection (e.g., after adding/removing stories), `selectedCollection` would return null with no recovery mechanism.

**Symptoms:**
- Leaf button completely unresponsive (no story playback)
- Issue persisted even after removing newly added story directories
- Logs showed: `selectedCollection is null` or `Failed to start story - collection not loaded`

**Solutions:**
1. Added `collectionsLoaded` state flag in `ExpandingView` that's set after `loadCollections()` completes. Leaf button now checks this flag before attempting to cycle content modes.
2. Enhanced `selectedCollection` getter to automatically fallback to first available collection when saved ID is invalid or null, then saves the new selection.

**Files Modified:**
- [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):143-149 - Added `collectionsLoaded` flag and wait for loading completion
- [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):590-596 - Added guard check in Leaf button onClick handler
- [StoryCollectionManager.kt](app/src/main/java/com/jmisabella/zrooms/StoryCollectionManager.kt):142-158 - Enhanced fallback logic in `selectedCollection` getter to auto-recover from invalid IDs

---

## 2026-01-17: Bug Fix - Story Title Overlay Re-trigger

### **BUG FIX**

Fixed bug where story title overlay would only show once and not reappear when toggling story/poetry mode back on, preventing users from accessing the story selector to switch collections.

**Root Cause:** The `StoryTitleOverlay` component used `remember(showTitle)` with a boolean flag. Once the flag was set to true and the overlay faded out, setting it to true again wouldn't retrigger the animation because the remembered state key didn't change.

**Symptoms:**
- Title overlay appears first time entering Story/Poetry mode
- After 4.5 second fade-out, overlay never reappears
- No way to access story selector to switch collections
- Users stuck on currently selected story

**Solution:** Changed from boolean flag to integer counter (`titleTriggerCount`). Each time content mode switches to STORY or POETRY, the counter increments, forcing the overlay animation to retrigger via `LaunchedEffect(triggerCount)`.

**Files Modified:**
- [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):217-223 - Changed from boolean `showStoryTitle` to integer `titleTriggerCount` that increments on each mode change
- [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):725-730 - Updated StoryTitleOverlay call to pass `triggerCount` instead of `showTitle`
- [StoryTitleOverlay.kt](app/src/main/java/com/jmisabella/zrooms/StoryTitleOverlay.kt):34-48 - Changed parameter from `showTitle: Boolean` to `triggerCount: Int` and updated LaunchedEffect to trigger on count changes

---

## 2026-01-17: Critical Bug Fix - Collection ID Persistence

### **BUG FIX**

Fixed critical bug where story playback would fail after app restart or toggling story mode off and back on.

**Root Cause:** The `StoryCollection` data class was generating a new random UUID for each collection's `id` field every time collections were loaded. When the app saved the selected collection ID to SharedPreferences and then reloaded collections (e.g., after app restart or navigating back to a room), the saved UUID would not match any of the newly generated UUIDs, causing `selectedCollection` to return null.

**Symptoms:**
- Leaf button unresponsive (no story playback)
- Logs showed: `selectedCollection is null (collections count: 1)`
- Title overlay and navigation buttons appeared but no audio/captions

**Solution:**
1. Changed `id` from a constructor parameter with random UUID default to a computed property that returns `directoryName`. Since directory names are stable across app sessions, collection IDs are now consistent and persist correctly.
2. Added migration logic to detect old UUID-based IDs (contain dashes) and reset to default, ensuring smooth upgrade for existing users.

**Files Modified:**
- [StoryCollection.kt](app/src/main/java/com/jmisabella/zrooms/StoryCollection.kt):3-10 - Changed `id` from `val id: String = UUID.randomUUID().toString()` to computed property `val id: String get() = directoryName`
- [StoryCollectionManager.kt](app/src/main/java/com/jmisabella/zrooms/StoryCollectionManager.kt):78-83 - Added migration logic to detect and reset old UUID-based collection IDs

---

## 2026-01-17: Multi-Story Architecture Implementation

### **OVERVIEW**

Implemented multi-story architecture allowing the app to organize stories and poems into collections. Each story collection has its own chapters and thematically related poems, matching the iOS implementation. This is a significant architectural enhancement that enables scalable story content organization.

### **KEY FEATURES ADDED**

#### 1. Story Collections
- Stories organized in `assets/tts_content/` directory structure
- Each collection has `stories/` and `poems/` subdirectories
- Initial collection: "Signal Decay" with 8 chapters and 8 poems
- Automatic discovery of new story collections when added to assets

#### 2. Story Title Display
- Title appears for 4.5 seconds when toggling Leaf/Theater buttons
- Smooth fade-in (500ms) and fade-out (1000ms) animations
- Tappable title with chevron down indicator to open story selector
- Shows current story collection name formatted with title case

#### 3. Story Selection Dialog
- Shows all available story collections
- Displays metadata: chapter count and poem count for each collection
- Visual indication of currently selected collection (checkmark + highlighted background)
- Dark theme matching app aesthetic
- Remembers selected story across app sessions via SharedPreferences

#### 4. Per-Story Chapter Memory
- Each story remembers its own chapter position separately
- Stored as JSON map in SharedPreferences (key: "chapter_positions")
- Example: Signal Decay at chapter 4, another story at chapter 2
- Position preserved across app restarts

#### 5. Scoped Poetry Mode
- Theater button now plays poems ONLY from current story's collection
- Poems scoped to `tts_content/{story_name}/poems/` directory
- Random selection from current story's poem pool
- Maintains last-played tracking to avoid immediate repeats

#### 6. Auto-Selection for New Users
- New users automatically get "signal_decay" as default collection (if exists)
- Falls back to first available collection alphabetically if signal_decay doesn't exist
- Existing users with saved preferences unaffected

### **FILES ADDED**

- **[StoryCollection.kt](app/src/main/java/com/jmisabella/zrooms/StoryCollection.kt)** - Data model for story collections with nested StoryFile class for chapter tracking
- **[StoryCollectionManager.kt](app/src/main/java/com/jmisabella/zrooms/StoryCollectionManager.kt)** - Manager for collection scanning, persistence, and per-story chapter memory using GSON + SharedPreferences
- **[StoryTitleOverlay.kt](app/src/main/java/com/jmisabella/zrooms/StoryTitleOverlay.kt)** - Composable for title display with animated fade (4.5s duration)
- **[StorySelectionDialog.kt](app/src/main/java/com/jmisabella/zrooms/StorySelectionDialog.kt)** - Composable for story selection UI with metadata display

### **FILES MODIFIED**

#### [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt)
- **Line 24**: Added `storyCollectionManager` parameter to constructor
- **Lines 64-68**: Removed deprecated `currentChapterIndex` and `totalPresetStories` properties
- **Lines 120-132**: Deleted `countPresetStories()` function (no longer needed)
- **Lines 439-445**: Replaced `getSequentialStory()` to use AssetManager-based collection system
- **Lines 450-459**: Replaced `loadRandomPoemFile()` to use collection-scoped poem loading
- **Lines 486-509**: Replaced `skipToNextChapter()` with per-collection chapter tracking
- **Lines 515-538**: Replaced `skipToPreviousChapter()` with per-collection chapter tracking
- **Lines 683-696**: Updated auto-advance logic to use collection-based chapter management

#### [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt)
- **Lines 139-145**: Added StoryCollectionManager instantiation and collection loading
- **Lines 148-150**: Updated TextToSpeechManager constructor to include storyCollectionManager
- **Lines 158-159**: Added UI state variables for story title and selector dialogs
- **Lines 217-221**: Added LaunchedEffect to trigger title display on content mode changes
- **Lines 723-728**: Added StoryTitleOverlay composable to UI hierarchy
- **Lines 903-912**: Added StorySelectionDialog composable for story selection

### **FILE MIGRATION**

Migrated content from `res/raw/` to structured `assets/tts_content/` directory:

#### Stories (renamed with numeric prefixes for ordering):
- `preset_story1.txt` → `assets/tts_content/signal_decay/stories/01_prelude.txt`
- `preset_story2.txt` → `assets/tts_content/signal_decay/stories/02_awakening.txt`
- `preset_story3.txt` → `assets/tts_content/signal_decay/stories/03_diagnostics.txt`
- `preset_story4.txt` → `assets/tts_content/signal_decay/stories/04_exploration.txt`
- `preset_story5.txt` → `assets/tts_content/signal_decay/stories/05_revelation.txt`
- `preset_story6.txt` → `assets/tts_content/signal_decay/stories/06_descent.txt`
- `preset_story7.txt` → `assets/tts_content/signal_decay/stories/07_convergence.txt`
- `preset_story8.txt` → `assets/tts_content/signal_decay/stories/08_epilogue.txt`

#### Poems (kept similar naming):
- `preset_poem1.txt` → `assets/tts_content/signal_decay/poems/forty_years_waiting.txt`
- `preset_poem2-8.txt` → `assets/tts_content/signal_decay/poems/poem_02-08.txt`

#### Default Custom Content:
- Moved to `assets/tts_content/default_custom_story.txt`
- Moved to `assets/tts_content/default_custom_poem.txt`

### **TECHNICAL DETAILS**

#### Architecture Patterns Used:
- **Jetpack Compose** for UI (AnimatedVisibility, LaunchedEffect, Dialog)
- **Compose MutableState** for reactive state management (`mutableStateListOf`, `mutableStateOf`)
- **Manager Pattern** matching existing CustomStoryManager architecture
- **GSON** for JSON serialization of chapter positions in SharedPreferences
- **AssetManager** for runtime directory scanning and file access

#### Key Implementation Decisions:
- **0-based chapter indexing** (consistent with current implementation)
- **Directory names lowercase** (Android convention): `signal_decay` not `Signal_Decay`
- **Numeric prefixes mandatory** for story files: `01_`, `02_`, etc.
- **Poem files flexible naming** (selected randomly, no ordering required)
- **Custom stories/poems unchanged** (separate SharedPreferences system remains independent)

### **FUTURE STORY ADDITION PROCESS**

To add new story collections:
1. Create directory: `assets/tts_content/my_story_name/`
2. Create subdirectories: `stories/` and `poems/`
3. Add numbered story files: `01_chapter1.txt`, `02_chapter2.txt`, etc.
4. Add poem files with any names: `poem1.txt`, `my_poem.txt`, etc.
5. Rebuild app
6. New story automatically appears in selection dialog as "My Story Name"

### **COMPATIBILITY**

- Backward compatible with existing custom stories/poems system
- All TTS functionality preserved (pauses, captions, voice selection, ambient volume)
- Button behavior unchanged (Leaf = Story, Theater = Poetry, circular navigation)
- Old `res/raw/` files can remain as backup but are no longer used

---

## 2026-01-17: Poetry Mode and Story Mode - Preset Content Only Verification

### **OVERVIEW**

Verified that the Poetry Mode (Theater Button) and Story Mode (Leaf Button) functionality correctly plays **only preset poems and stories**, excluding custom content from random selection. Custom poems and stories remain accessible exclusively through the dedicated Custom Content browser menu.

### **VERIFICATION COMPLETED**

**No changes were needed** - the implementation is already correct and working as intended.

#### Poetry Mode (Theater Button) - Preset Poems Only

**Files Reviewed:**
- [TextToSpeechManager.kt:475-534](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L475-L534)
- [ExpandingView.kt:563-600](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L563-L600)

**Implementation:**
- Theater button triggers `cycleContentMode()` which calls `startSpeakingRandomPoem()`
- `startSpeakingRandomPoem()` calls `loadRandomPoemFile()`
- `loadRandomPoemFile()` explicitly:
  1. Loads **only** preset poem files (`preset_poem1`, `preset_poem2`, etc.) from res/raw
  2. **Excludes custom poems** from random selection (lines 503-504)
  3. Comment at line 503: "Custom poems excluded from random selection - only presets play via Poetry button"

#### Story Mode (Leaf Button) - Preset Stories Only

**Files Reviewed:**
- [TextToSpeechManager.kt:394-452, 458-472, 536-590](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L394-L452)

**Implementation:**
- Leaf button triggers sequential story playback via `startSpeakingSequentialStory()`
- Random story mode uses `loadRandomStoryFile()` which:
  1. Loads **only** preset story files (`preset_story1`, `preset_story2`, etc.) from res/raw
  2. **Excludes custom stories** from random selection (lines 425-426)
  3. Comment at line 425: "Custom storys excluded from random selection - only presets play via Leaf button"

#### Custom Content Access

**Custom Poems and Stories Are:**
- Accessible **only** through the dedicated Custom Content browser (Format Quote button)
- Playable directly from the custom content list views
- Never included in random preset poem/story rotation

### **USER EXPERIENCE**

✅ **Poetry Mode (Theater Button):** Always plays a random **preset poem**
✅ **Story Mode (Leaf Button):** Always plays **preset stories** sequentially
✅ **Custom Content:** Only accessible through Custom Content browser menu
✅ **Separation Maintained:** Clear distinction between preset and custom content playback

### **TECHNICAL DETAILS**

**Random Selection Logic:**
- Both `loadRandomPoemFile()` and `loadRandomStoryFile()` dynamically discover preset files using resource identifier lookup
- Files are named: `preset_poem1.txt`, `preset_poem2.txt`, ... and `preset_story1.txt`, `preset_story2.txt`, ...
- Custom content stored separately in SharedPreferences via `CustomPoetryManager` and `CustomStoryManager`
- No code path allows custom content to be selected by the Leaf or Theater buttons

**Anti-Repeat Logic:**
- Tracks `lastPlayedPoem` and `lastPlayedStory` to avoid immediate repetition
- Filters out the last played item when selecting the next random preset

### **IMPACT**

This verification confirms that users can only trigger custom poems/stories through explicit selection in the Custom Content browser. The Leaf and Theater buttons will never accidentally play user's custom content, maintaining clear separation between preset and custom content experiences.

---

## 2026-01-15: Smart Default Voice Selection - Daniel Voice Auto-Detection

### **OVERVIEW**

Enhanced the Text-to-Speech system to intelligently detect and use the Daniel (English, United States) voice as the default when it's already available on the user's device. This provides a better out-of-box experience for users who have this high-quality voice pre-installed without requiring any downloads or configuration.

**IMPORTANT:** This update includes a one-time migration that resets all existing voice preferences to apply the smart default voice selection. After this migration, user voice preferences will be fully respected going forward.

### **CHANGES IMPLEMENTED**

#### 1. One-Time Voice Preference Migration

**File Modified:**
- [VoiceManager.kt:55-79](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt#L55-L79)

**Changes:**
- Added migration flag `PREF_VOICE_MIGRATION_V1` to track migration status
- Implemented `performOneTimeMigration()` method that runs once on first app launch after update
- Migration clears existing voice preferences to enable smart default behavior

**Migration Logic:**
```kotlin
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
```

**Why This Migration?**
- Ensures all users (both new and existing) benefit from the Daniel voice auto-detection
- Existing users with custom voice settings will be reset to smart defaults
- After migration, any new voice selections users make will be respected permanently
- Migration only runs once per device installation

#### 2. Smart Voice Detection in VoiceManager

**File Modified:**
- [VoiceManager.kt:105-149](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt#L105-L149)

**Changes:**
- Modified `getPreferredVoice()` method to check for Daniel voice availability when enhanced voice is disabled
- Added logic to detect Daniel voice by its voice code "iom"
- Implements automatic fallback chain: Daniel (if available) → System default voice

**Implementation Details:**
```kotlin
if (!useEnhancedVoice.value) {
    // Check if Daniel voice (voice code "iom") is available
    val danielVoice = availableVoices.value.firstOrNull { voice ->
        val name = voice.name.lowercase()
        val voiceCode = when {
            name.contains("-x-") -> {
                name.substringAfter("-x-").substringBefore("-").substringBefore("#")
            }
            else -> ""
        }
        voiceCode == "iom" && isVoiceAvailable(voice)
    }

    if (danielVoice != null) {
        return danielVoice
    }

    return null // Fall back to system default
}
```

### **BEHAVIOR**

**When Enhanced Voice is Disabled (Default Setting):**
1. App checks if Daniel voice is already downloaded on the device
2. If found and available, Daniel is automatically used for TTS
3. If not found, falls back to system default voice (intentionally robotic voice)

**When Enhanced Voice is Enabled:**
- User's explicitly selected voice takes precedence (unchanged behavior)
- Existing voice selection logic remains intact

### **USER EXPERIENCE IMPROVEMENTS**

- ✅ **Zero Friction:** Users with Daniel pre-installed get better voice quality automatically
- ✅ **No Forced Downloads:** Users without Daniel aren't prompted to download anything
- ✅ **User Choice Respected:** After one-time migration, explicit voice selections in settings take precedence
- ✅ **Maintains App Philosophy:** App remains 100% offline with no required downloads
- ✅ **Better Default Experience:** Many newer devices (especially Google Pixel, Samsung flagship) come with Daniel pre-installed
- ✅ **One-Time Reset:** Existing users will have voice preferences reset once to benefit from smart defaults, then preferences are honored

### **TECHNICAL DETAILS**

**Voice Detection Logic:**
- Identifies Daniel by Google TTS voice code "iom" (IOM Male)
- Only uses Daniel if `isVoiceAvailable()` returns true (voice is downloaded and functional)
- Preserves existing friendly name mapping: "iom" → "Daniel"

**Priority Hierarchy (Updated):**
1. User's selected enhanced voice (when enhanced mode enabled)
2. Daniel voice (when enhanced mode disabled AND Daniel is available)
3. High-quality fallback voices (when enhanced mode enabled but selected voice unavailable)
4. System default voice (final fallback)

### **IMPACT**

This change provides an improved default experience for users while maintaining the app's core principle of being fully functional offline without requiring any downloads. Users benefit from higher quality narration if their device already has the capability, with zero configuration required.

**Migration Impact:**
- All existing users will have their voice preferences reset on the next app launch after this update
- This one-time reset ensures everyone benefits from the new Daniel voice auto-detection
- After the migration, users who customize their voice settings will have those preferences fully respected
- New users will automatically get the smart default behavior without any migration needed

---

## 2026-01-15 23:45 EST: Responsive Closed Caption Box Height and Layout Optimization

### **OVERVIEW**

Enhanced the closed caption display with responsive sizing and improved layout positioning to match the iOS implementation:
- Increased caption box height in portrait mode from 100.dp to 300.dp (approximately 35-40% of screen height)
- Maintained compact 100.dp height in landscape mode to avoid covering sliders
- Repositioned skip chapter buttons to sit just above the caption box without overlapping
- Adjusted slider positioning higher in landscape mode for optimal screen space usage
- Lowered caption box in landscape mode for better spacing between sliders and captions

### **CHANGES IMPLEMENTED**

#### 1. Responsive Caption Box Height

**File Modified:**
- [ScrollableStoryTextDisplay.kt:99-102, 143](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt#L99-L102)

**Changes:**
- Added orientation detection using `LocalConfiguration`
- Implemented dynamic height based on screen orientation:
  - **Portrait mode:** 300.dp (matching iOS 300 points)
  - **Landscape mode:** 100.dp (compact to avoid covering UI elements)

**Impact:**
- Users can now read significantly more text at once in portrait mode without scrolling
- Landscape mode keeps caption box compact to preserve visibility of duration and ambient volume sliders
- Matches the iOS implementation for consistent cross-platform experience

#### 2. Repositioned Skip Chapter Buttons

**File Modified:**
- [ExpandingView.kt:632-634, 652](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L632-L634)

**Changes:**
- Updated button positioning to be orientation-aware
- Skip buttons now positioned just above caption box with 10.dp gap:
  - **Portrait mode:** 490.dp from bottom (180 + 300 + 10)
  - **Landscape mode:** 190.dp from bottom (80 + 100 + 10)

**Impact:**
- Skip buttons no longer overlap the caption box
- Proper spacing maintained in both orientations
- Buttons remain easily accessible while captions are displayed

#### 3. Optimized Slider Positioning for Landscape Mode

**File Modified:**
- [ExpandingView.kt:124-126, 400](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L124-L126)

**Changes:**
- Added global orientation detection at function level
- Reduced top padding for sliders in landscape mode:
  - **Portrait mode:** 40.dp top padding (unchanged)
  - **Landscape mode:** 8.dp top padding (moved much higher)

**Impact:**
- Duration and ambient volume sliders now sit near the top of the screen in landscape
- Maximizes available space for caption box without overlap
- Top slider almost touches the top of the screen in landscape as desired

#### 4. Adjusted Caption Box Bottom Spacing

**File Modified:**
- [ExpandingView.kt:633, 645](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L633)

**Changes:**
- Made bottom padding responsive to orientation:
  - **Portrait mode:** 180.dp from bottom (unchanged)
  - **Landscape mode:** 80.dp from bottom (lowered for better spacing)

**Impact:**
- In landscape mode, creates proper vertical spacing between sliders and caption box
- Caption box positioned optimally to not interfere with other UI elements
- Maintains consistent spacing from bottom control buttons in both orientations

### **TECHNICAL DETAILS**

**Imports Added:**
- `android.content.res.Configuration`
- `androidx.compose.ui.platform.LocalConfiguration`

**Responsive Design Pattern:**
All layout adjustments use a consistent pattern:
```kotlin
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val parameter = if (isLandscape) landscapeValue else portraitValue
```

### **USER EXPERIENCE IMPROVEMENTS**

- ✅ **Portrait Mode:** Much taller caption box (300.dp) allows reading multiple paragraphs at once
- ✅ **Landscape Mode:** Compact layout keeps sliders, captions, and buttons all visible without overlap
- ✅ **Skip Buttons:** Properly positioned above captions in both orientations
- ✅ **Consistent with iOS:** Matches the iOS app's layout and caption box sizing
- ✅ **Better Accessibility:** More text visible at once improves readability for users relying on closed captions

---

## 2026-01-15: Default Ambient Volume and TTS Voice Volume Adjustments

### **OVERVIEW**

Adjusted default audio levels for better user experience:
- Increased default ambient volume from 85% to 100%
- Lowered TTS voice volume from 0.23 to 0.20 for better balance with ambient audio

### **CHANGES IMPLEMENTED**

#### 1. Default Ambient Volume Increased to 100%

**Files Modified:**
- [AudioService.kt:42](app/src/main/java/com/jmisabella/zrooms/AudioService.kt#L42)
- [TextToSpeechManager.kt:38](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L38)

**Changes:**
- `AudioService.kt`: Changed `targetAmbientVolume` from `0.8f` (80%) to `1.0f` (100%)
- `TextToSpeechManager.kt`: Changed `ambientVolume` default from `0.8f` (80%) to `1.0f` (100%)

**Impact:**
- New users will now start with full ambient volume by default
- Users can still adjust volume down using the slider if desired
- Provides more immersive audio experience out of the box

#### 2. TTS Voice Volume Reduced

**File Modified:**
- [VoiceManager.kt:45](app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt#L45)

**Changes:**
- Changed `VOICE_VOLUME` constant from `0.23f` to `0.20f`

**Impact:**
- TTS narration voice is now slightly quieter relative to ambient audio
- Improves balance between voice and background sounds
- Makes for a more pleasant listening experience during guided stories and poetry readings

---

## 2026-01-15 20:30 EST: Closed Caption Paragraph Structure Preservation

### **OVERVIEW**

Fixed Android closed captions to preserve original paragraph structure in historical text. Previously, each sentence appeared on its own line in the scroll history. Now historical paragraphs are grouped and displayed as text blocks, matching the original story formatting and the iOS implementation.

### **PROBLEM STATEMENT**

**What was broken:**
- Closed captions correctly showed sentence-by-sentence display for currently spoken text
- However, **historical text** (previously spoken sentences) did not preserve paragraph structure
- Each sentence appeared on a separate line instead of being grouped back into paragraphs
- Made it difficult to read scrolled-back content, breaking the natural flow of the story

**Expected behavior:**
1. **Current sentence**: Display ONLY the current sentence being read (highlighted, larger font)
2. **Previous sentences** in same paragraph: Show dimmed above current sentence
3. **Historical text** (when scrolling up): Group sentences back into their **original paragraphs** for readability

### **TECHNICAL APPROACH**

The fix uses a **paragraph boundary marker** system, identical to the iOS implementation:

1. **Text Processing**: Split paragraphs into sentences and add `<<PARAGRAPH_BREAK>>` marker after last sentence of each paragraph
2. **Marker Management**: Strip marker before speech (never spoken), preserve as `<<PB>>` in phrase history for display
3. **Display Logic**: Group historical sentences between markers back into paragraph blocks

### **CHANGES IMPLEMENTED**

#### 1. Sentence Boundary Detection

**File:** [TextToSpeechManager.kt:240-289](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L240-L289)

**New Function:** `splitIntoSentences(text: String): List<String>`
- Character-by-character parsing with lookahead for accurate sentence detection
- Detects sentence endings: `.`, `!`, `?` followed by whitespace
- Handles common abbreviations to avoid false splits:
  - Personal titles: `Dr.`, `Mr.`, `Mrs.`, `Ms.`
  - Common terms: `vs.`, `etc.`, `e.g.`, `i.e.`
- Returns list of sentences with punctuation preserved

**Example:**
```kotlin
Input: "Dr. Smith is here. How are you? Great!"
Output: ["Dr. Smith is here.", "How are you?", "Great!"]
```

#### 2. Paragraph Break Marker Insertion

**File:** [TextToSpeechManager.kt:291-339](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L291-L339)

**Updated Function:** `addAutomaticPauses(text: String): String`

**Key Changes:**
- Changed from splitting on single newlines `\n` to blank lines `\n\s*\n` (proper paragraph detection)
- Added `<<PARAGRAPH_BREAK>>` marker after last sentence of each paragraph
- Updated pause timing:
  - 0.5s between sentences within paragraphs (was 0s)
  - 2s between paragraphs (unchanged)

**Data Flow:**
```
Input text:
"Sentence one. Sentence two.

Next paragraph here."

↓ Processing ↓

Output with markers:
"Sentence one. (0.5s)
Sentence two.<<PARAGRAPH_BREAK>> (2s)
Next paragraph here.<<PARAGRAPH_BREAK>> (0.5s)"
```

#### 3. Marker Preservation in Storage

**File:** [TextToSpeechManager.kt:341-389](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L341-L389)

**Updated Function:** `extractPhrasesWithPauses(text: String): List<Pair<String, Long>>`

**Key Changes:**
- Preserves `<<PARAGRAPH_BREAK>>` as shortened `<<PB>>` marker in phrase storage
- Marker stays with the sentence in the `phraseHistory` array
- Allows display component to identify paragraph boundaries

**Example Storage:**
```kotlin
phraseHistory = [
    "Sentence one.",
    "Sentence two.<<PB>>",  // End of paragraph 1
    "Next paragraph here.<<PB>>"  // End of paragraph 2
]
```

#### 4. Marker Stripping Before Speech

**File:** [TextToSpeechManager.kt:610-618](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L610-L618)

**Updated Function:** `speakNextPhrase()`

**Critical Change:**
- Strip `<<PB>>` marker before passing text to TTS engine
- **IMPORTANT:** Marker is NEVER spoken aloud
- Marker only exists for display logic

```kotlin
val cleanedPhrase = phrase
    .replace("<<PB>>", "")  // Strip paragraph marker
    .replace("-", " ")       // Existing cleanup
    // ... other cleanup
```

#### 5. Paragraph Grouping Helper

**File:** [ScrollableStoryTextDisplay.kt:31-77](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt#L31-L77)

**New Data Class:** `ParagraphItem`
```kotlin
private data class ParagraphItem(
    val sentences: List<String>,
    val isCurrentParagraph: Boolean
)
```

**New Function:** `groupIntoParagraphs(phraseHistory: List<String>, currentPhrase: String): List<ParagraphItem>`

**Logic:**
1. Iterate through phrase history
2. Accumulate sentences into current paragraph
3. When `<<PB>>` marker found, create new `ParagraphItem` and start next paragraph
4. Identify which paragraph contains the currently spoken sentence
5. Return list of paragraph items for display

**Example:**
```kotlin
Input phraseHistory:
["Sentence 1.", "Sentence 2.<<PB>>", "Sentence 3."]

Output:
[
    ParagraphItem(sentences=["Sentence 1.", "Sentence 2."], isCurrentParagraph=false),
    ParagraphItem(sentences=["Sentence 3."], isCurrentParagraph=true)
]
```

#### 6. Updated Display Logic

**File:** [ScrollableStoryTextDisplay.kt:120-206](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt#L120-L206)

**Previous Implementation:**
```kotlin
itemsIndexed(phraseHistory) { index, phrase ->
    // Each phrase displayed individually
    Text(text = phrase, ...)
}
```

**New Implementation:**
```kotlin
itemsIndexed(paragraphs) { _, paragraph ->
    if (paragraph.isCurrentParagraph) {
        // Current paragraph: show sentences individually
        Column {
            paragraph.sentences.forEach { sentence ->
                Text(
                    text = sentence,
                    fontSize = if (isCurrentSentence) 18.sp else 16.sp,
                    color = if (isCurrentSentence) White else White.copy(0.7f)
                )
            }
        }
    } else {
        // Historical paragraph: combine into block
        Text(
            text = paragraph.sentences.joinToString(" "),
            fontSize = 16.sp,
            color = White.copy(0.7f)
        )
    }
}
```

**Display Behavior:**

**Current Paragraph** (contains actively spoken sentence):
- Each sentence displayed individually
- Current sentence: 18sp, bright white, medium weight
- Previous sentences in same paragraph: 16sp, dimmed (70% opacity)

**Historical Paragraphs** (already completed):
- All sentences combined with `joinToString(" ")`
- Restores original paragraph structure
- Single text block: 16sp, dimmed (70% opacity)
- `<<PB>>` markers automatically stripped via `replace()` calls

#### 7. Scroll Position Updates

**File:** [ScrollableStoryTextDisplay.kt:124-133, 222-228](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt#L124-L133)

**Updated:** Auto-scroll and "New text" button to use `paragraphs.size` instead of `phraseHistory.size`
- Ensures correct scroll target since display now uses paragraph items, not raw phrases

### **EXAMPLE BEHAVIOR**

**Story Structure** (preset_story1.txt excerpt):
```
You sign the contract because you need to eat. Because the rent on your
compartment in the Ganymede hab-ring is three weeks overdue. Because your
daughter needs new pressure seals for her school suit.

The contract is forty-seven pages of legal text optimized for corporate
liability protection. You scroll through terms like "acceptable casualty rates."

Someone has to pick through the ruins.
```

**While Speaking Paragraph 1:**
```
[Dimmed] You sign the contract because you need to eat.
[Dimmed] Because the rent on your compartment in the Ganymede hab-ring is three weeks overdue.
[BRIGHT, LARGE] Because your daughter needs new pressure seals for her school suit.
```

**After Completing Paragraph 1, While Speaking Paragraph 2:**
```
[Historical Block] You sign the contract because you need to eat. Because the rent on your compartment in the Ganymede hab-ring is three weeks overdue. Because your daughter needs new pressure seals for her school suit.

[Dimmed] The contract is forty-seven pages of legal text optimized for corporate liability protection.
[BRIGHT, LARGE] You scroll through terms like "acceptable casualty rates."
```

**After Completing Paragraph 2:**
```
[Historical Block] You sign the contract because you need to eat. Because the rent on your compartment in the Ganymede hab-ring is three weeks overdue. Because your daughter needs new pressure seals for her school suit.

[Historical Block] The contract is forty-seven pages of legal text optimized for corporate liability protection. You scroll through terms like "acceptable casualty rates."

[Current] Someone has to pick through the ruins.
```

### **KEY DESIGN PRINCIPLES**

1. **Minimal Overhead**: Simple string markers (`<<PB>>`), no complex data structures
2. **Never Speak Markers**: Always strip before TTS synthesis
3. **Current vs Historical**: Different display modes for active vs completed paragraphs
4. **Natural Pauses**: 0.5s between sentences, 2s between paragraphs
5. **Readability**: Historical text looks like original paragraphs when scrolling back

### **TESTING**

- ✅ Project builds successfully with no compilation errors
- ✅ Implementation matches iOS approach documented in TODO.md
- ✅ Ready for testing with multi-paragraph stories (preset_story1.txt through preset_story8.txt)

### **FILES MODIFIED**

1. [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt)
   - Added `splitIntoSentences()` function (lines 240-289)
   - Updated `addAutomaticPauses()` to insert paragraph markers (lines 291-339)
   - Updated `extractPhrasesWithPauses()` to preserve markers (lines 341-389)
   - Updated `speakNextPhrase()` to strip markers before TTS (lines 610-618)

2. [ScrollableStoryTextDisplay.kt](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt)
   - Added `ParagraphItem` data class (lines 31-37)
   - Added `groupIntoParagraphs()` helper function (lines 39-77)
   - Updated display logic for paragraph-based rendering (lines 120-206)
   - Updated scroll targets to use paragraph count (lines 124-133, 222-228)

---

## 2026-01-14 22:00 EST: Circular Chapter Navigation & UI Fixes

### **OVERVIEW**

Implemented circular navigation for story chapters (wrapping from last to first and vice versa) and fixed critical UI issues where navigation buttons were unresponsive and poorly positioned.

### **CHANGES IMPLEMENTED**

#### 1. Circular Chapter Navigation

**File:** [TextToSpeechManager.kt:490-530](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L490-L530)

**Previous Behavior:**
- `skipToNextChapter()` returned `false` when at last chapter (stopping navigation)
- `skipToPreviousChapter()` returned `false` when at first chapter (stopping navigation)

**New Behavior:**
- Clicking next on last chapter wraps to first chapter (preset_story1.txt)
- Clicking previous on first chapter wraps to last chapter
- Both functions now always return `true` (unless no chapters exist)

**Critical Bug Fix:**
- Added `contentMode` preservation in both navigation functions (lines 502-504, 525-527)
- **Issue:** `startSpeakingSequentialStory()` calls `stopSpeaking()` which sets `contentMode = OFF`
- **Impact:** This was causing the Leaf button to turn off, hiding navigation buttons and closed captions
- **Solution:** Save contentMode before calling `startSpeaking`, restore it immediately after

#### 2. Navigation Button Touch Input Fix

**File:** [ExpandingView.kt:649-695](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L649-L695)

**Problem:** Navigation buttons were not responding to taps - taps were propagating to parent Column's tap gesture handler which called `dismiss()`

**Root Cause Analysis:**
- Parent Column has `detectTapGestures` modifier (line 380-389) that catches all taps
- Navigation buttons are siblings to Column in parent Box
- `zIndex` alone wasn't sufficient to prevent tap propagation
- The `clickable` modifier was still allowing events to bubble up

**Solution:**
- Replaced `clickable` modifier with `pointerInput(Unit)` + `detectTapGestures` on each button
- This properly consumes tap events at the button level before they reach the parent

#### 3. UI Spacing Improvements

**File:** [ExpandingView.kt:635, 645](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L635)

Increased spacing to prevent overlapping UI elements:
- **Closed captions box:** `bottom = 140.dp` → `180.dp` (40dp increase)
- **Navigation buttons:** `bottom = 250.dp` → `290.dp` (40dp increase)

**Before:** Navigation buttons and closed captions were touching the bottom control buttons (Leaf, Settings, etc.)

**After:** Clean visual separation with proper spacing

### **DEBUGGING PROCESS**

Added temporary debug logging to identify the root cause:
```kotlin
println("DEBUG: Next chapter button tapped! contentMode=$contentMode")
println("DEBUG: Column tap gesture detected at offset=$offset, contentMode=$contentMode")
```

Logs revealed:
1. Button tap handlers WERE firing correctly
2. `contentMode` was correctly `MEDITATION` before and after `skipToNextChapter()`
3. But UI was still disappearing, indicating contentMode was being changed elsewhere
4. Traced to `startSpeakingWithPauses() → stopSpeaking() → contentMode = OFF`

### **FILES MODIFIED**

| File | Changes |
|------|---------|
| [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) | Circular navigation logic, contentMode preservation |
| [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) | Button touch input handling, spacing adjustments |

### **TESTING CHECKLIST**

- [x] Navigation buttons respond to taps without dismissing view
- [x] Leaf button stays green/enabled when navigating chapters
- [x] Closed captions remain visible during chapter navigation
- [x] Voice continues playing new chapter after navigation
- [x] Clicking next on last chapter wraps to first chapter
- [x] Clicking previous on first chapter wraps to last chapter
- [x] Navigation buttons have proper spacing from bottom controls
- [x] No UI overlap between navigation buttons and control buttons

---

## 2026-01-14: Story Chapter Mode - Sequential Playback with Navigation

### **OVERVIEW**

Major feature pivot: The guided story feature (Leaf button) has been repurposed to play sequential story chapters instead of random storys. This aligns the Android app with corresponding iOS changes.

### **CHANGES IMPLEMENTED**

#### 1. TTS Pause Timing Adjustments

**File:** [TextToSpeechManager.kt:245-285](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L245-L285)

- **Removed** automatic 2-second pauses after sentences
- **Reduced** paragraph pauses from 4 seconds to 2 seconds
- **Added** `(0s)` markers between sentences to create closed caption breaks without actual audio pauses
- Manual pause markers like `(2s)` or `(1.5m)` in content files still work as expected

**Result:** Narration flows more naturally with shorter pauses, while closed captions update sentence-by-sentence.

#### 2. Preset-Only Random Selection

**Files Modified:** [TextToSpeechManager.kt:335-336, 418-419](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L335-L336)

- **Removed** custom storys from the random story pool
- **Removed** custom poems from the random poetry pool
- Custom content remains accessible through the dedicated content browser (quote button)

**Rationale:** The Leaf button and Poetry mode now only play preset content for consistent story/poem experience.

#### 3. Sequential Story Chapter Playback

**File:** [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt)

New properties and methods added:

- `currentChapterIndex` - Persisted to SharedPreferences, tracks which chapter the user is on (lines 64-66)
- `totalPresetStorys` - Cached count of available chapters (line 68)
- `countPresetStorys()` - Dynamically counts preset files (lines 126-136)
- `getSequentialStory()` - Loads current chapter by index (lines 393-406)
- `startSpeakingSequentialStory()` - Starts playing current chapter (lines 474-479)
- `skipToNextChapter()` - Advances to next chapter if available (lines 485-495)
- `skipToPreviousChapter()` - Goes back to previous chapter if available (lines 501-510)

**Behavior Changes:**

- Leaf button now plays chapters sequentially starting from chapter 1 (or last saved position)
- Chapter progress persists across app restarts via SharedPreferences
- Auto-advances to next chapter when current chapter completes (with 1-second pause)
- Poetry mode remains random (not sequential)

#### 4. Skip Button UI

**File:** [ExpandingView.kt:638-680](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt#L638-L680)

Added `<` and `>` navigation buttons:

- **Position:** Above closed caption area (`bottom = 250.dp`)
- **Visibility:** Only appear when in MEDITATION mode (story chapters)
- **Styling:** Semi-transparent black circles with chevron icons
- Left button calls `skipToPreviousChapter()`
- Right button calls `skipToNextChapter()`

#### 5. TTS Character Filtering

**File:** [TextToSpeechManager.kt:595-607](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L595-L607)

Fixed issue where TTS was literally saying "dash" and "hash" for `-` and `#` characters:

```kotlin
val cleanedPhrase = phrase
    .replace("-", " ")   // Dash becomes space (natural for hyphenated words)
    .replace("#", "")    // Hash removed
    .replace("*", "")    // Asterisk removed
    .replace("_", "")    // Underscore removed
    .replace("~", "")    // Tilde removed
```

**Note:** Closed captions still display the original text with these characters - only the TTS audio is cleaned.

### **FILES MODIFIED**

| File | Changes |
|------|---------|
| [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) | Pause timing, preset-only selection, sequential playback, chapter navigation, TTS character filtering |
| [ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) | Added skip button UI with < > navigation |

### **TESTING CHECKLIST**

- [x] Build compiles successfully
- [ ] TTS plays without sentence pauses, 2s pauses between paragraphs
- [ ] Manual pause markers `(2s)` still work
- [ ] Leaf button only plays preset storys (not custom)
- [ ] Poetry button only plays preset poems (not custom)
- [ ] Story plays sequentially from chapter 1
- [ ] Chapter progress persists after app restart
- [ ] Skip buttons appear only in MEDITATION mode
- [ ] < and > buttons navigate chapters correctly
- [ ] Auto-advance works when chapter completes
- [ ] Closed captions update sentence-by-sentence
- [ ] TTS does not say "dash" or "hash" for - and # characters
- [ ] Skip buttons don't overlap with other UI elements

---

## 2026-01-03 18:45 EST: Scrollable Closed Caption History with Auto-Scroll

### **THE FEATURE**

Users can now scroll through the full transcript of story and poetry narration as it's being spoken. Previously, only the current phrase and previous phrase were visible - once text scrolled off, it was lost forever. Now users can scroll back to review earlier phrases they may have missed, while the voice continues narrating in the background.

**User Impact:**
- Full scrollable history of all spoken phrases during the session
- Ability to review missed content without stopping playback
- Auto-scrolls to show new text when user is at the bottom
- Manual scroll stops auto-scrolling so users can read at their own pace
- "New text" indicator button appears when scrolled up and new content arrives
- Tapping "New text" scrolls back to current phrase and resumes auto-scrolling

This feature mirrors the iOS implementation completed on 2026-01-03.

### **THE IMPLEMENTATION**

**Component Architecture:**

Three main components were modified/created to implement scrollable captions:

1. **TextToSpeechManager.kt** - Phrase History State Management
   - Added `phraseHistory: List<String>` state to track all spoken phrases (line 47-48)
   - Added `hasNewCaptionContent: Boolean` state for "New text" indicator (line 50)
   - Modified `onStart()` callback to append phrases to history without duplicates (lines 87-90)
   - Reset history when starting new content in `startSpeakingWithPauses()` (lines 156-157)
   - Clear history when stopping playback in `stopSpeaking()` (lines 194-195)
   - Clear history when narration completes in `didFinishSpeaking()` (lines 544-545)

2. **ScrollableStoryTextDisplay.kt** - NEW Scrollable Component
   - Uses `LazyColumn` with `rememberLazyListState()` for efficient virtualization
   - Fixed 100dp height with semi-transparent dark background (55% opacity, 16dp rounded corners)
   - `derivedStateOf` for accurate bottom detection (lines 51-60)
   - `userHasScrolledUp` state flag tracks manual scrolling (line 46)
   - Auto-scroll behavior: scrolls to new phrases when `!userHasScrolledUp` (lines 71-79)
   - Drag gesture detection sets `userHasScrolledUp = true` when user starts scrolling (lines 107-117)
   - Resets `userHasScrolledUp = false` when user reaches bottom (lines 63-68)
   - "New text" button with down arrow icon (lines 139-182)
   - Current phrase highlighted: white, 18sp, medium weight
   - Previous phrases dimmed: 70% opacity, 16sp, normal weight

3. **ExpandingView.kt** - Integration
   - Replaced `StoryTextDisplay` with `ScrollableStoryTextDisplay` (lines 624-633)
   - Connected phrase history from TTS manager
   - Connected `hasNewCaptionContent` state with callback for updates
   - Maintained same positioning (140dp above buttons)

### **PERFORMANCE OPTIMIZATIONS**

**Key Optimizations to Avoid iOS Lag Issues:**

The iOS version initially experienced significant lag and unresponsiveness when implementing this feature. The Android implementation was designed to avoid these issues:

1. **`LazyColumn` Virtualization**
   - Only renders visible items plus small buffer
   - Automatically handles large phrase lists efficiently
   - Native Android component optimized for scrolling

2. **`derivedStateOf` for Bottom Detection**
   - Recomputes only when scroll state actually changes
   - Avoids unnecessary recompositions
   - More efficient than manual state tracking

3. **Simplified State Management**
   - Single `userHasScrolledUp` boolean instead of multiple flags
   - Clear state transitions reduce complexity
   - Fewer edge cases to handle

4. **Instant Scroll Updates**
   - Uses `scrollState.scrollToItem()` (not animated) for auto-scroll
   - Prevents animation lag on rapid phrase changes
   - "New text" button uses `animateScrollToItem()` for smooth UX

5. **Minimal Drag Detection**
   - Simple `detectDragGestures` on `onDragStart` only
   - Doesn't interfere with native scroll behavior
   - Lightweight touch handling

### **USER EXPERIENCE**

**Scrolling Behavior:**

1. **Auto-Scroll Mode (Default)**
   - New phrases appear automatically as they're spoken
   - Caption box stays at bottom showing latest text
   - User doesn't need to do anything

2. **Manual Scroll Mode (User scrolls up)**
   - User drags/swipes upward to review earlier text
   - Auto-scrolling stops immediately
   - Voice continues narrating in background
   - "New text" button appears when new content arrives

3. **Return to Auto-Scroll**
   - User scrolls back to bottom manually → auto-scroll resumes
   - User taps "New text" button → animates to bottom, auto-scroll resumes

**Visual Design:**

```
╭──────────────────────────────────╮
│ [Earlier phrase - dimmed 70%]    │
│ [Earlier phrase - dimmed 70%]    │  ← Scrollable
│ [Earlier phrase - dimmed 70%]    │     100dp height
│ [Current phrase - white, bold]   │     Semi-transparent
│         ⬇ New text              │  ← Button (conditional)
╰──────────────────────────────────╯
```

### **TESTING CHECKLIST**

**Functionality Verified:**
✅ Caption history builds up as phrases are spoken
✅ Current phrase highlighted (white, 18sp, medium weight)
✅ Previous phrases dimmed (70% opacity, 16sp)
✅ Voice continues narrating while user scrolls
✅ Auto-scroll works when user is at bottom
✅ Auto-scroll stops when user scrolls up
✅ "New text" indicator appears correctly
✅ Tapping "New text" scrolls back to bottom
✅ History clears when story ends
✅ History resets when starting new story

**Performance Verified:**
✅ No lag when opening room view
✅ Smooth scrolling with many phrases
✅ Build successful with no compilation errors

### **FILES CREATED**

- [app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt](app/src/main/java/com/jmisabella/zrooms/ScrollableStoryTextDisplay.kt) - New scrollable caption component (182 lines)

### **FILES MODIFIED**

- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt):
  - Added `phraseHistory` state variable (lines 47-48)
  - Added `hasNewCaptionContent` state variable (line 50)
  - Updated `onStart()` callback to append to history (lines 87-90)
  - Reset history in `startSpeakingWithPauses()` (lines 156-157)
  - Clear history in `stopSpeaking()` (lines 194-195)
  - Clear history in `didFinishSpeaking()` (lines 544-545)

- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):
  - Replaced `StoryTextDisplay` with `ScrollableStoryTextDisplay` (lines 624-633)
  - Connected phrase history and new content state from TTS manager

### **CODE STATISTICS**

- New component: 182 lines
- TextToSpeechManager changes: +6 lines
- ExpandingView changes: ~10 lines modified
- Total: ~200 lines added/modified

### **REFERENCE DOCUMENTATION**

- iOS implementation: Completed 2026-01-03 17:30
- Implementation guide: [ANDROID_SCROLLABLE_CAPTIONS_IMPLEMENTATION_GUIDE.md](ANDROID_SCROLLABLE_CAPTIONS_IMPLEMENTATION_GUIDE.md)
- Performance lessons learned from iOS applied to Android implementation

---

## 2025-12-31 20:55 EST: Fixed Closed Caption Box Remaining Visible After Narration Ends

### **THE BUG**

When story or poetry narration completes, the closed caption box (semi-transparent dark modal window) remains visible on screen even though there's no text being spoken. The caption text itself disappears, but the empty dark background box persists.

**User Impact:**
After listening to a story or poem in its entirety, users see an empty dark box floating at the bottom of the screen, which looks unprofessional and confusing. The box should disappear completely when narration ends.

### **THE CAUSE**

The `StoryTextDisplay` component was already correctly implemented with conditional rendering logic (line 33):
```kotlin
visible = isVisible && (currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty())
```

However, in [TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt), when narration completed successfully (in the `didFinishSpeaking()` method around line 531), the code was NOT clearing the `currentPhrase` and `previousPhrase` variables. These variables remained populated with the last spoken text, causing the caption box to stay visible.

**What was happening:**
1. Narration completes → `isSpeaking` set to false
2. `contentMode` kept as MEDITATION/POETRY (intentionally, so button stays colored)
3. `utteranceQueue` cleared
4. ❌ BUT `currentPhrase` and `previousPhrase` were NOT cleared
5. Result: Caption box condition `(currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty())` still true → box stays visible

### **THE FIX**

Modified the `didFinishSpeaking()` method in [TextToSpeechManager.kt:540-543](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt#L540-L543) to clear caption text when narration completes:

**Code Changes:**
```kotlin
} else {
    // All done - content completed successfully
    isSpeaking = false
    isCustomMode = false
    utteranceQueue.clear()
    currentUtteranceIndex = 0

    // NEW: Clear caption text so the closed caption box disappears
    currentPhrase = ""
    previousPhrase = ""
    pendingPhrase = null

    // Set the content completion flag for wake-up greeting
    prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, true).apply()
}
```

### **THE RESULT**

The closed caption box now properly disappears when story/poetry narration completes:
- Narration ends → Caption text cleared → Entire caption box (including background) disappears ✅
- Wake-up greeting functionality still works correctly (relies on `PREF_CONTENT_COMPLETED` flag, not caption text)
- Content mode button stays colored (MEDITATION/POETRY mode remains active until user manually toggles it off)
- Clean, professional UI when narration finishes

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) (lines 540-543)

**Note:** This fix was already implemented in the iOS version. Reference: [IMPLEMENTATION_GUIDE_ANDROID_CAPTION_FIX.md](IMPLEMENTATION_GUIDE_ANDROID_CAPTION_FIX.md)

---

## 2025-12-31 20:43 EST: Fixed Wake-Up Greeting Voice Selection

### **THE BUG**

When users set a waking alarm sound and enable story mode or poetry mode, the app plays a brief greeting ("Welcome back", etc.) when the alarm goes off. However, this greeting was always spoken using the default woman's voice, even when the user had selected a different voice (such as one of the male voices) for their story/poetry narration.

**User Impact:**
Users who selected a specific voice (e.g., Alex, Daniel, Michael) for their story or poetry narration would hear the default voice for the wake-up greeting instead of their chosen voice, creating an inconsistent experience.

### **THE CAUSE**

In [AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt), the `greetingTts` TextToSpeech instance was initialized with hardcoded settings:
- Language was hardcoded to `Locale.US` (default system voice)
- Speech rate was hardcoded to `0.6f`
- Pitch was hardcoded to `0.58f`

The greeting TTS initialization did not use the `VoiceManager` to apply the user's selected voice preferences, unlike the main story/poetry TTS which correctly uses `VoiceManager.getPreferredVoice()`.

### **THE FIX**

Modified the `playWakeUpGreeting()` method in [AudioService.kt:454-480](app/src/main/java/com/jmisabella/zrooms/AudioService.kt#L454-L480) to refresh voice settings **at playback time** instead of only at service initialization.

**Key Insight:** The original fix attempted to set the voice during service initialization, but those settings could become stale by the time the greeting actually played. The correct solution is to refresh the voice settings immediately before speaking the greeting.

**Code Changes:**
```kotlin
private fun playWakeUpGreeting() {
    if (!isGreetingTtsInitialized) return

    // NEW: Refresh voice settings to match current user selection
    val voiceManager = VoiceManager.getInstance(this)
    val preferredVoice = voiceManager.getPreferredVoice()

    if (preferredVoice != null) {
        greetingTts?.setVoice(preferredVoice)
    } else {
        greetingTts?.language = Locale.US
    }

    val speechRate = voiceManager.getSpeechRateMultiplier(preferredVoice)
    greetingTts?.setSpeechRate(speechRate)
    greetingTts?.setPitch(1.0f)

    // Select greeting and speak...
}
```

Also updated the initialization in [AudioService.kt:137-158](app/src/main/java/com/jmisabella/zrooms/AudioService.kt#L137-L158) to set initial voice preferences, though the playback-time refresh is what ensures the correct voice is always used.

### **THE RESULT**

The wake-up greeting now respects the user's voice selection:
- Users who select Alex, Daniel, Michael, or any other voice will hear that same voice for the "Welcome back" greeting
- Voice settings are refreshed at playback time, ensuring the greeting always uses the current selection
- Speech rate automatically adjusts based on voice quality (enhanced voices at natural speed, default voice slightly slower)
- Consistent voice experience throughout the entire story/poetry alarm workflow

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt) (lines 137-158, 454-480)

---

## 2025-12-27 14:30 EST: UX Pivot - Closed Captioning Modal Window Design

### **THE REQUEST**

Replace the gradient-based closed captioning design with a semi-transparent dark modal window, positioned in the lower portion of the screen above the "room #" label. This is a strategic UX pivot after multiple failed attempts across several sessions to make the gradient extend properly to the bottom of the screen.

### **THE PROBLEM**

**Multiple Failed Gradient Attempts:**
Over several conversation threads, we attempted to fix the closed captioning gradient positioning issues:
- Attempted to move the gradient to the actual bottom of the screen
- Attempted to make the area below the closed caption black
- Multiple iterations of padding adjustments (96dp bottom, restructured padding from Box to Column, orientation-aware padding)
- Issues persisted with either gaps between gradient and screen edge (landscape) or overlap with navigation buttons (portrait)

**Root Issue:**
The gradient-based approach proved unreliable on Android. While this gradient design works well in the iOS version of the app, achieving the same polished look on Android was not feasible despite multiple attempts.

**Strategic Decision:**
Rather than continue attempting to fix the gradient approach, we pivoted to a different UX design that is more reliable and maintainable while still providing an excellent user experience.

### **THE SOLUTION**

**New Design: Semi-Transparent Dark Modal Window**

Replaced the edge-to-edge gradient background with a rounded, semi-transparent dark modal window that:
1. Contains the closed captioning text (previous line + current line)
2. Has rounded corners (16dp) consistent with other UI cards in the app
3. Uses medium transparency (55% opacity) for good readability while letting background show through
4. Has 24dp horizontal margins from screen edges (floating appearance)
5. Positioned in lower portion of screen, above the "room #" label
6. Maintains all existing animations (fade + slide effects)
7. Maintains existing text styling (previous phrase faded/smaller, current phrase bold/larger)

**Visual Appearance:**
```
┌─────────────────────────────────────┐
│         Room Background             │
│                                     │
│                                     │
│    ╭──────────────────────────╮   │ ← Modal window
│    │ [prev phrase - faded]    │   │
│    │ [current phrase - bold]  │   │
│    ╰──────────────────────────╯   │
│    room 12                         │ ← Room label
│    [gear] [quote] [clock] [leaf]   │ ← Button row
└─────────────────────────────────────┘
```

**Code Changes:**

**StoryTextDisplay.kt:**
```kotlin
// BEFORE (Gradient approach):
Box(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.4f),
                    Color.Black.copy(alpha = 0.7f)
                ),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY
            )
        ),
    contentAlignment = Alignment.BottomCenter
)

// AFTER (Modal window approach):
Box(
    modifier = Modifier
        .wrapContentHeight()
        .padding(horizontal = 24.dp) // Margins from screen edges
        .background(
            color = Color.Black.copy(alpha = 0.55f), // Semi-transparent dark modal
            shape = RoundedCornerShape(16.dp)        // Rounded corners
        ),
    contentAlignment = Alignment.Center
)
```

**ExpandingView.kt:**
```kotlin
// BEFORE:
StoryTextDisplay(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(bottom = if (isPortrait) 48.dp else 0.dp)
)

// AFTER:
StoryTextDisplay(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 90.dp) // Position above room label and buttons
)
```

### **FILES MODIFIED**

- [app/src/main/java/com/jmisabella/zrooms/StoryTextDisplay.kt](app/src/main/java/com/jmisabella/zrooms/StoryTextDisplay.kt):
  - Removed `Brush` import (no longer needed)
  - Added `RoundedCornerShape` import
  - Replaced gradient background with rounded semi-transparent dark modal (Color.Black.copy(alpha = 0.55f))
  - Added 24dp horizontal margins to create floating modal appearance
  - Changed contentAlignment from `Alignment.BottomCenter` to `Alignment.Center`
  - Simplified internal padding to uniform 16dp on all sides
  - Updated documentation comment to reflect modal window design

- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt):
  - Removed orientation-aware padding logic (no longer needed)
  - Removed `.fillMaxWidth()` modifier (modal sizes itself with margins)
  - Changed bottom padding to fixed 90dp to position modal above room label
  - Updated comment to reflect modal window positioning

### **USER EXPERIENCE IMPROVEMENTS**

**Before (Gradient approach):**
- Gradient sometimes had gaps at screen bottom (landscape)
- Gradient sometimes overlapped navigation buttons (portrait)
- Required complex orientation-specific padding logic
- Multiple failed attempts to achieve polished look
- Inconsistent behavior across orientations

**After (Modal window approach):**
- Clean, distinct visual separation from background
- Reliable positioning above room label in all orientations
- Rounded corners match other UI elements (consistent design language)
- Semi-transparent background provides excellent readability
- Simplified positioning logic (no orientation-specific code needed)
- Floating appearance (24dp margins) creates modern, polished look

**Benefits:**
- ✅ **Reliable** - No more gradient positioning issues
- ✅ **Consistent** - Works the same way in all orientations
- ✅ **Maintainable** - Simpler code, easier to understand and modify
- ✅ **Polished** - Rounded corners and floating appearance match app design
- ✅ **Readable** - Semi-transparent dark background ensures good text contrast
- ✅ **Future-proof** - No complex edge cases or orientation-specific logic

**Preserved Features:**
- ✅ Same smooth animations (fade + slide)
- ✅ Same text styling (previous phrase faded/smaller, current phrase bold/larger)
- ✅ Same two-line display format
- ✅ Same show/hide behavior based on story state

### **DESIGN RATIONALE**

While the iOS version of the app uses an edge-to-edge gradient for closed captioning, this Android version now uses a modal window approach. This is a pragmatic decision based on:
1. Multiple failed attempts to achieve the gradient look on Android
2. Time investment vs. diminishing returns
3. The modal window approach provides an equally good (arguably better) user experience
4. The modal design is more maintainable and reliable

Sometimes the best solution is to pivot to a different approach rather than continue fighting with a problematic implementation. The modal window design is a strategic win.

---

## 2025-12-27 10:45: UX Improvement - Default Ambient Audio Volume Reduced to 80%

### **THE REQUEST**

Change the default ambient audio volume from 100% to 80% to provide a better balance between the story voice narration and ambient background audio.

### **THE PROBLEM**

**User Experience Issue:**
At the default 100% ambient volume level, the story voice (fixed at 23% volume) was too quiet relative to the ambient background audio. This created a suboptimal listening experience where users had to manually adjust the ambient slider down to hear the story guidance clearly.

**Testing Results:**
After testing across all ambient audio track types (white noise, dark ambient, bright ambient, and classical compositions), the user found that 80% ambient volume provided the ideal ratio between voice clarity and ambient atmosphere.

### **THE SOLUTION**

Updated the default ambient volume from 100% (1.0f) to 80% (0.8f) in both TextToSpeechManager and AudioService. This change:
1. Provides better out-of-box experience for new users
2. Ensures story voice narration is clearly audible over ambient audio
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
- Improved story quality with optimal voice-to-ambient ratio
- Tested and verified across all 4 ambient audio styles

**No Breaking Changes:**
- Users can still adjust ambient volume from 0-100% using the slider
- Setting is not persisted, so always resets to the new 80% default on app launch
- Story voice volume remains unchanged at 23%

---

## 2024-12-26 16:30: Bug Fix - Voice Settings Not Applied to Storys

### **THE REQUEST**

User reported that when selecting a different voice in the Voice Settings dialog, the selected voice was not being applied to guided storys. The story would continue using the default system voice instead of the user's chosen voice.

### **THE PROBLEM**

**Root Cause:**
The `VoiceManager.setPreferredVoice()` method was saving the voice selection to preferences but was not enabling the `useEnhancedVoice` flag. The `getPreferredVoice()` method checks if enhanced voice is enabled, and returns `null` (default system voice) when disabled, even if a voice has been selected.

**Impact:**
- Users could select enhanced voices but they would not be used for story playback
- Voice previews worked correctly (they used their own TTS instance)
- Actual story playback ignored the voice selection and used the default system voice
- User experience was confusing as the settings appeared to save but had no effect

**Code Flow Analysis:**
1. User selects voice in VoiceSettingsView → calls `voiceManager.setPreferredVoice(voice)`
2. `setPreferredVoice()` saves voice name to preferences but doesn't enable enhanced voice
3. When story plays → `TextToSpeechManager.applyVoiceSettings()` calls `voiceManager.getPreferredVoice()`
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
5. Tap the story button (leaf icon) to play a guided story
6. Verify the story uses the selected voice (not the default system voice)

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
- Alarm and story state transitions
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
   - Story file loading
   - Replaced with silent error handling

5. **MainActivity.kt** - 1 statement removed
   - onCreate lifecycle logging

6. **CustomStoryManager.kt** - 1 statement removed
   - Default story loading errors

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
5. **Get More Voices Button**: The deep-link to TTS settings added complexity without clear benefit, and instructions for downloading voices were unnecessarily detailed for a story app

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
   - Rationale: Users can still access TTS settings through device settings if needed, but this complexity doesn't belong in a story app's voice selector

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
- Focus on story experience rather than TTS management

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

## 2025-12-25 23:27: Enhanced Opening Phrase Variety in Preset Storys

### **THE REQUEST**

The user noticed that an excessive number of preset storys began with the exact same literal phrase: "Before we begin, consider this." This repetitive opening created a monotonous user experience for regular story users. The request was to introduce significant variety in the opening phrases while maintaining the overall contemplative meaning and tone.

**Initial Analysis:**
- 27 out of 36 story files used "Before we begin,"
- 13 of those used the generic "consider this" after it
- This lack of variety made storys feel formulaic and less engaging

### **THE SOLUTION**

**Implementation Strategy:**
Developed a diverse collection of 20+ opening phrase variations across 5 categories, distributed thoughtfully across all story files based on their content and sources. The approach prioritized attribution-specific phrases for storys with named sources (Marcus Aurelius, Tao Te Ching, etc.) while using varied invitations and tone-setters for original content.

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

**Category 3: Attribution-Specific (for storys with sources)**
- "In the words of William Wordsworth, from I Wandered Lonely As A Cloud"
- "Marcus Aurelius reminds us, from his Storys"
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
- One story (preset_story4.txt) starts directly with instructions, no preamble

**Preserved "Before we begin" instances (2-3 total, as requested):**
- "A reflection before we begin" (story 10)
- "Before we begin, from the Serenity Prayer" (story 25)
- "Before we begin, from an old Zen saying" (story 28)

### **CHANGES MADE**

Updated 27 out of 36 story files with varied opening phrases:

**Storys with Attribution-Specific Phrases:**
- preset_story1.txt: "In the words of William Wordsworth..."
- preset_story2.txt: "Marcus Aurelius reminds us..."
- preset_story6.txt: "Wisdom from Ralph Waldo Emerson's Self-Reliance"
- preset_story8.txt: "From Lao Tzu's Tao Te Ching, these words"
- preset_story11.txt: "The Bhagavad Gita teaches us"
- preset_story15.txt: "An ancient teaching from the Tao Te Ching"
- preset_story20.txt: "Wisdom from the Dhammapada"
- preset_story21.txt: "Ancient Buddhist wisdom teaches"
- preset_story30.txt: "From the Upanishads, this teaching"

**Storys with Reflection & Invitation Phrases:**
- preset_story3.txt: "Here's a reflection"
- preset_story7.txt: "Let us settle in with"
- preset_story12.txt: "To guide our journey today"
- preset_story13.txt: "A thought to hold"
- preset_story14.txt: "Let's begin with this insight"
- preset_story16.txt: "Something to ponder"
- preset_story17.txt: "To set our intention"
- preset_story18.txt: "Reflect on this"
- preset_story19.txt: "Here's a thought to carry with us"
- preset_story22.txt: "Something to reflect upon"
- preset_story24.txt: "A moment to contemplate"
- preset_story26.txt: "As we prepare"
- preset_story29.txt: "Consider these words"
- preset_story31.txt: "To ground this practice"
- preset_story32.txt: "Let these words guide us"
- preset_story33.txt: "A reminder for our practice"
- preset_story34.txt: "May we hold this truth"
- preset_story35.txt: "We begin with"

### **DISTRIBUTION & VARIETY ACHIEVED**

**Before:**
- 27 files using "Before we begin,"
- 13 files with "Before we begin, consider this"
- Extremely repetitive, formulaic feel

**After:**
- Only 2-3 files retain "Before we begin" (10, 25, 28)
- 20+ unique opening phrases across all categories
- Variety distributed evenly based on story content and sources
- Each story feels more unique and thoughtfully crafted

### **FILES MODIFIED**

All story files in `zz-time/Storys/`:
- preset_story1.txt through preset_story35.txt
- Total: 27 files updated with new opening phrases
- 3 files retained "Before we begin" for variety
- Multiple files already had "Let's begin with this thought" variations (kept for continuity)


## 2025-12-25 15:00 EST

**Feature Enhancement:** Prevent Consecutive Story Repeats - Ensures variety in story selection

**User Experience:** When users toggle the leaf button on to play a story, then toggle it off to stop, and toggle it back on again, the app now guarantees that the second story will be different from the first. This prevents the jarring experience of hearing the exact same story twice in a row when toggling the leaf button multiple times.

**Implementation Details:**

Added story history tracking to prevent immediate repeats:

1. **Last Played Tracking:**
   - New variable `lastPlayedStory: String?` stores the full text of the most recently played story
   - Updated when story is selected in `loadRandomStoryFile()`
   - Persists for the app session (not saved to disk)

2. **Smart Random Selection:**
   - When 2+ storys available AND a previous story exists:
     - Filters out `lastPlayedStory` from available pool
     - Selects randomly from remaining storys
   - When only 1 story available:
     - Plays that single story (no choice)
   - First time playing (no previous story):
     - Selects randomly from all available storys

3. **Selection Logic:**
```kotlin
val selectedStory = if (allStorys.size > 1 && lastPlayedStory != null) {
    // Filter out the last played story and pick from remaining
    val availableStorys = allStorys.filter { it != lastPlayedStory }
    availableStorys.random()
} else {
    // First time playing or only one story available
    allStorys.random()
}
lastPlayedStory = selectedStory
```

**User Scenarios:**

*Scenario 1: Typical Toggle On/Off/On (Multiple Storys Available)*
- User toggles leaf ON → Story #12 plays
- User toggles leaf OFF → Story stops
- User toggles leaf ON → Story #27 plays (guaranteed NOT #12)
- User toggles leaf OFF → Story stops
- User toggles leaf ON → Any story EXCEPT #27 plays

*Scenario 2: Only One Story Available*
- User has deleted all presets except one, no custom storys
- User toggles leaf ON → Story #1 plays
- User toggles leaf OFF → Story stops
- User toggles leaf ON → Story #1 plays (only choice available)

*Scenario 3: With 70 Total Storys (35 presets + 35 custom)*
- Each toggle ensures variety: 69 different options each time
- Prevents repetitive experience even with frequent toggling
- Last played story only persists during app session

**Benefits:**
- Better user experience with guaranteed variety
- Prevents frustration from hearing same story immediately after stopping it
- Works seamlessly with both preset and custom storys
- No impact on first-time story selection
- Minimal memory overhead (stores single story text)

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Added `lastPlayedStory` tracking (line 46), updated `loadRandomStoryFile()` with smart selection logic (lines 324-342)

## 2025-12-25 14:30 EST

**Bug Fix:** Voice Volume Consistency - Unified TTS voice volume across all playback instances

**Problem:** The voice preview feature in VoiceSettingsView was playing at full volume (1.0), which was significantly louder than the story narration volume (0.23f). This created a jarring experience where users would hear previews at one volume level but storys at a much quieter level. Additionally, the VOICE_VOLUME constant was duplicated in multiple files (TextToSpeechManager and referenced in AudioService), violating DRY principles and creating maintenance risk.

**Solution:** Consolidated voice volume management by:
1. Moving the VOICE_VOLUME constant to VoiceManager as the single source of truth
2. Adding volume parameter to preview TTS in VoiceManager.previewVoice()
3. Updating all TTS instances to reference VoiceManager.VOICE_VOLUME

**Implementation Details:**

All three TTS voice instances now use the same volume (0.23f):
- **Story narration** (TextToSpeechManager.speakNextPhrase())
- **Voice preview** (VoiceManager.previewVoice())
- **Wake-up greeting** (AudioService.playWakeUpGreeting())

**Code Changes:**

```kotlin
// VoiceManager.kt - Single source of truth for voice volume
companion object {
    // Voice volume (shared across story playback and preview)
    const val VOICE_VOLUME = 0.23f
}

// Preview now uses same volume as storys
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
- Voice previews now play at the same comfortable volume as storys (0.23f instead of 1.0)
- Consistent audio experience across all TTS features
- No more jarring volume differences when testing voices

## 2025-12-25

**Feature:** Enhanced Voice Quality - Optional high-quality TTS voices for story narration

**User Experience:** Users can now optionally select higher-quality TTS voices for story narration while maintaining the default "artificial" voice aesthetic as the default. A new gear icon (settings button) appears in the story view (ExpandingView), positioned as the leftmost button in the bottom row. Tapping this icon opens voice settings where users can toggle "Enhanced Voice" on/off and select from available high-quality voices. Each voice can be previewed with a sample story phrase before selection. The app maintains 100% offline functionality - only voices that don't require network connectivity are shown.

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
   - Separate preview TTS instance to avoid interfering with story playback
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
   - Preview functionality: Sample text "Welcome to your story practice. Take a deep breath and relax."
   - Preview state management: Clicking new preview immediately stops previous one
   - "Get More Voices" button: Deep-links to Android TTS settings for voice management
   - Info section (scrollable, at bottom of list when Enhanced Voice ON):
     - Explains voices are managed by device's TTS engine
     - Notes many voices come pre-installed, others require download (100-500MB each)
     - Provides path: Settings → Accessibility → Text-to-Speech → Speech Services by Google → Settings icon → Voice selection
     - Clarifies enhanced voices stored in device system storage, not app
   - Close button returns to story view and refreshes voice settings

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
   - Button order: gear → quote (custom story) → clock (alarm timer) → leaf (story toggle)
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
- Storys use default system voice at 0.8x speed, 1.0 pitch
- Exactly same experience as before this feature was added
- No behavior change

*Scenario 2: Enhanced Voice Discovery*
- User taps gear icon in story view
- Opens voice settings
- Toggles "Enhanced Voice" ON
- Scrollable list of voices appears with friendly names
- Taps preview button on "Samantha" → Hears sample at 1.0x speed
- Taps preview button on "Alex" → Previous preview stops, new one starts
- Selects "Samantha" (checkmark appears)
- Closes settings
- Next story uses Samantha voice at natural speed (1.0x)
- Preference persists across app restarts

*Scenario 3: Voice Requires Download*
- User selects voice with "Needs Download" indicator
- Voice won't work until downloaded
- Taps "Get More Voices" button
- Deep-linked to Android TTS settings
- Downloads voice via Google Text-to-Speech app
- Returns to z rooms app
- Selected voice now available for storys

*Scenario 4: Enhanced Voice Unavailable (Fallback)*
- User previously selected enhanced voice "Victoria"
- Voice gets deleted from system settings
- App fallback priority:
  1. Try to use Victoria → Not available
  2. Try any QUALITY_HIGH voice for en-US → If available, use it
  3. Fall back to default system voice → Always available
- Story plays successfully with fallback voice

**Android-Specific Implementation Notes:**

Unlike iOS where voices download automatically on first use, Android requires manual voice download through the Google Text-to-Speech app settings. The implementation handles this with:
- Clear "Needs Download" indicators on unavailable voices
- "Get More Voices" button with deep-link to `com.android.settings.TTS_SETTINGS`
- Info section explaining download process step-by-step
- Fallback logic when selected voice unavailable

**Key Technical Decisions:**

1. **Offline-Only Voices**: Only shows voices with `isNetworkConnectionRequired == false`
2. **Singleton Pattern**: VoiceManager ensures single TTS instance for voice discovery
3. **Separate Preview TTS**: Prevents interference with story playback
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

**Feature:** Wake-Up Greeting - Personalized audio greetings for users who complete storys before their alarm

**User Experience:** When a user completes a guided story the night before and wakes to a non-SILENCE alarm, the app now speaks a brief, randomized greeting phrase approximately 5 seconds after the alarm begins playing. This creates a gentle, personalized wake-up experience that acknowledges the user's story practice.

**Implementation Details:**

The wake-up greeting feature consists of three main components:

1. **Story Completion Tracking** (TextToSpeechManager.kt):
   - Added `PREF_MEDITATION_COMPLETED` SharedPreferences key to track successful story completion
   - When story starts: Flag is cleared to ensure fresh state
   - When story completes fully: Flag is set to true AND `isPlayingStory` state remains true (keeping leaf button green)
   - When story is manually stopped: Flag is cleared
   - This allows the system to distinguish between completed vs. interrupted storys

2. **Greeting TTS Engine** (AudioService.kt):
   - Added dedicated `greetingTts` TextToSpeech instance separate from story TTS
   - Initialized with same voice settings as storys (speech rate: 0.6f, pitch: 0.58f)
   - Five randomized greeting phrases: "Welcome back", "Greetings", "Here we are", "Returning to awareness", "Welcome back to this space"
   - Intentionally avoids time-specific words like "morning" (user might wake from afternoon nap)

3. **Alarm Integration** (AudioService.kt):
   - Modified `startAlarm()` to check story completion flag when alarm triggers
   - If alarm is SILENCE (selectedAlarmIndex is null or -1): No greeting plays, ambient fades to nothing
   - If alarm is non-SILENCE AND story was completed: Greeting is scheduled
   - `scheduleWakeUpGreeting()` waits 5 seconds after alarm audio starts
   - `playWakeUpGreeting()` speaks random phrase and clears completion flag
   - Greeting plays only ONCE (does not repeat with alarm loop)

**Bug Fix - Leaf Button State:**

Fixed a significant UX issue where the leaf button would turn grey (toggle off) when a story completed successfully, making it impossible to distinguish between a completed story and one that was never started.

**Previous Behavior:**
- Story completes → `isPlayingStory` set to false → Leaf turns grey
- User has no visual indication that story completed successfully

**New Behavior:**
- Story completes → `isPlayingStory` stays true → Leaf stays green
- User can see story completed successfully (green leaf)
- User can manually toggle leaf off by tapping it if desired
- Wake-up greeting system can detect successful completion

**Trigger Conditions (ALL must be met for greeting to play):**
1. Alarm/wake time is reached
2. Non-SILENCE waking room selected (alarm sound plays)
3. Guided story completed successfully the night before (leaf is green)

**Exclusions:**
- Does NOT play if SILENCE is selected (no alarm sound means no greeting)
- Does NOT play if story was interrupted or manually stopped
- Does NOT play if no story was started

**User Scenarios:**

*Scenario 1: Typical Weekday Morning (Greeting Plays)*
- User toggles story on before sleep
- Story plays to completion → Leaf stays green
- Alarm triggers with classical music (rooms 31-35)
- ~5 seconds later: Random greeting phrase spoken
- Flag cleared, ready for next night

*Scenario 2: Weekend Morning with SILENCE (No Greeting)*
- User toggles story on before sleep
- Story completes → Leaf stays green
- Alarm triggers with SILENCE selected
- Ambient audio fades to nothing
- No greeting plays (SILENCE means user doesn't want alarm sound)

*Scenario 3: No Story (No Greeting)*
- User sleeps without toggling story
- Alarm triggers with classical music
- No greeting plays (story wasn't completed)

*Scenario 4: Interrupted Story (No Greeting)*
- User toggles story on but manually stops it mid-session
- Alarm triggers with classical music
- No greeting plays (story wasn't completed successfully)

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Added SharedPreferences tracking for story completion state, fixed leaf button to stay green after completion
- [app/src/main/java/com/jmisabella/zrooms/AudioService.kt](app/src/main/java/com/jmisabella/zrooms/AudioService.kt) - Added wake-up greeting TTS engine, scheduling logic, and alarm integration

## 2025-12-21
- Added better variation to the preset storys.

## 2025-12-21

**Problem:** Two story playback issues were discovered after the previous fix:
1. **Resume Instead of New Selection**: When toggling the leaf button off and back on, the app would sometimes resume the previous story instead of selecting a new random one, reducing variety and creating a confusing user experience.
2. **Caption/Audio Delay on First Play**: When first toggling story on, there was a significant delay between when the closed caption text appeared and when the TTS audio actually started playing, creating an awkward pause.

**Root Cause:**
1. The `startSpeakingWithPauses()` function checked `if (isSpeaking)` and would exit early, which could cause timing issues where the old story state wasn't fully cleared before starting a new one. The `startSpeakingRandomStory()` also checked `isSpeaking`, preventing a fresh story from being selected.
2. The caption text (`currentPhrase`) was being set immediately when calling `tts?.speak()`, but the actual TTS audio had a processing delay before it started, causing the caption to appear well before the audio began.

**Solution:**
1. Modified `startSpeakingWithPauses()` to always call `stopSpeaking()` first, ensuring a clean state before starting any new story. Removed the `isSpeaking` check from `startSpeakingRandomStory()` so it always selects a new random story.
2. Introduced a `pendingPhrase` variable that stores the phrase text, and only updates `currentPhrase` (which controls the caption display) when the TTS engine's `onStart()` callback is triggered, ensuring perfect synchronization between caption display and audio playback.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Fixed story restart logic and synchronized caption display with TTS audio start

## 2025-12-20

**Problem:** Random story selection was only using the first 10 preset storys (preset_story1 through preset_story10) and completely ignoring preset storys 11-35. This significantly reduced story variety and meant users were missing out on 25 of the 35 available preset storys. This bug was discovered in the iOS version of the app and was confirmed to exist in the Android version as well.

**Root Cause:** In the `loadRandomStoryFile()` function within TextToSpeechManager.kt, the loop that loads preset story files was incorrectly limited to `for (i in 1..10)` instead of iterating through all 35 preset story files.

**Solution:** Replaced the hardcoded loop range (`1..10`, later `1..35`) with dynamic discovery logic that automatically finds all available preset story files without any upper limit. The function now uses a while loop that continues checking for `preset_story$i` files until it finds a gap, ensuring all preset storys are included regardless of how many exist. This future-proofs the code so that adding new preset storys (e.g., preset_story36 through preset_story40) will automatically work without any code changes. Custom storys were already being loaded correctly via the `forEach` loop over all custom story entries.

**Content Update:** All 35 preset story files have been updated to incorporate more breathwork guidance, enhancing the story experience with structured breathing exercises integrated throughout the guided sessions.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt](app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt) - Updated loop range to include all 35 preset storys
- All 35 preset story text files (preset_story1.txt through preset_story35.txt) - Enhanced with additional breathwork guidance

## 2025-12-13 16:30 EST

**Problem:** After exiting from ExpandingView (room view) back to ContentView, the bottom row and half of the second-to-last row of room tiles incorrectly displayed as rectangles instead of squares in portrait mode. Additionally, on first app launch in portrait mode followed by rotation to landscape mode, the tiles appeared oversized and didn't fit on the screen properly. This regression was introduced by the recent change that added `android:configChanges="keyboardHidden|orientation|screenSize"` to prevent Activity recreation during rotation.

**Root Cause:** The `android:configChanges` attribute prevents the Activity from being destroyed and recreated during orientation changes. While this successfully preserved story playback state (as intended), it created an unintended side effect: Compose's `BoxWithConstraints` component in ContentView was not automatically recomposing when orientation changed. This caused the aspect ratio calculations (`aspect = itemW.value / itemH.value`) to retain stale dimension values from the previous orientation, resulting in incorrectly sized tiles.

**Solution:** Wrapped the `LazyVerticalGrid` component inside a `androidx.compose.runtime.key(configuration.orientation)` block. This forces Compose to completely recompose the grid layout whenever the device orientation changes, ensuring that BoxWithConstraints recalculates dimensions and the aspect ratio is always computed with current screen dimensions. The fix maintains the benefit of preserving story state during rotation while ensuring the tile grid layout correctly adapts to orientation changes.

**Link to Previous Change:** This issue was a direct regression from the 2025-12-13 13:03 EST change which added `android:configChanges="keyboardHidden|orientation|screenSize"` to AndroidManifest.xml. That change successfully prevented story interruption during rotation but inadvertently broke the responsive layout behavior of ContentView's tile grid.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/ContentView.kt](app/src/main/java/com/jmisabella/zrooms/ContentView.kt) - Added orientation-keyed recomposition wrapper around LazyVerticalGrid

## 2025-12-13 15:00 EST

**Problem:** In portrait mode, the closed captioning text appeared too low on the screen and overlapped with the device's navigation buttons (back, home, recent apps), making the captions difficult to read. This issue did not occur in landscape mode.

**Solution:** Added orientation-aware padding using LocalConfiguration to detect device orientation. In portrait mode, 48dp of bottom padding is applied to lift the closed captioning above the navigation buttons. In landscape mode, no additional padding is applied, keeping the gradient flush with the screen edge. This provides optimal readability in portrait mode while maintaining the polished appearance in landscape mode.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) - Added orientation detection and conditional bottom padding

## 2025-12-13 14:45 EST

**Problem:** In landscape mode, the closed captioning gradient overlay had a visible gap between the bottom of the gradient and the bottom edge of the screen, making the app appear unpolished. This gap was not present in portrait mode, creating an inconsistent visual experience.

**Solution:** Restructured the padding in StoryTextDisplay component to apply padding only to the text content Column (24dp bottom) instead of the outer gradient Box (which previously had 96dp bottom padding). Also removed the additional 40dp bottom padding that was being applied in ExpandingView. This allows the gradient to extend fully to the screen edge in both orientations while maintaining appropriate spacing for the text content.

**Files Modified:**
- [app/src/main/java/com/jmisabella/zrooms/StoryTextDisplay.kt](app/src/main/java/com/jmisabella/zrooms/StoryTextDisplay.kt) - Moved padding from Box to Column for proper gradient extension
- [app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt](app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt) - Removed bottom padding from StoryTextDisplay modifier

## 2025-12-13 13:03 EST

**Problem:** When a guided story was playing and the device was rotated from portrait to landscape (or vice versa), the Leaf button would become untoggled and the guided story speech along with closed captioning would stop playing, while the ambient audio continued. This created an inconsistent user experience where rotation interrupted the story session.

**Solution:** Added `android:configChanges="keyboardHidden|orientation|screenSize"` attribute to the MainActivity declaration in AndroidManifest.xml. This configuration change handling prevents the Activity from being destroyed and recreated during rotation, preserving the story playback state. Now when users rotate their device during a story session, the guided story and closed captions continue playing seamlessly, just like the ambient audio does.

**Files Modified:**
- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) - Added configChanges attribute to MainActivity
