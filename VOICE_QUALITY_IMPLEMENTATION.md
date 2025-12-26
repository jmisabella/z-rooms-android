# High-Quality Voice Implementation Guide

## Overview

This document outlines the implementation strategy for adding optional high-quality voice downloads to the z rooms meditation app on both iOS and Android platforms. This feature allows users to optionally use enhanced, more natural-sounding voices while maintaining the current artificial voice aesthetic as the default.

---

## iOS Implementation

### Technical Foundation

**Framework**: `AVSpeechSynthesizer` (AVFoundation)

**Voice Quality Tiers**:
- **Compact** (~50-100MB) - Basic quality, pre-installed on all devices
- **Enhanced** (~100-300MB) - Improved quality, downloadable
- **Premium/Neural** (~300-500MB+) - Highest quality with neural TTS, downloadable

**Key Advantage**: Voice downloads are managed by iOS as system-level assets, **not** bundled with the app. They don't count against app size and are stored in shared system space.

### Architecture Changes

#### 1. Voice Management Module

**New File**: `VoiceManager.swift`

**Responsibilities**:
- Discover available voices on device
- Check voice quality and download status
- Provide voice selection logic with fallback
- Handle voice preference persistence

**Key APIs**:
```swift
// Discover all voices
AVSpeechSynthesisVoice.speechVoices()

// Check voice quality
voice.quality // Returns: .default, .enhanced, or .premium

// Voice properties
voice.name          // "Samantha", "Alex", etc.
voice.identifier    // Unique identifier for persistence
voice.language      // "en-US", "en-GB", etc.
```

#### 2. Settings UI

**New File**: `VoiceSettingsView.swift`

**Components**:
- Toggle: "Use Enhanced Voice" (off by default)
- Voice picker: Shows available enhanced/premium voices when toggle is on
- Quality indicators: Show which voices are downloaded vs. need download
- Preview button: Test selected voice with sample phrase
- Storage info: Estimated size for each voice option

**Access Method**:
- Add gear/settings icon to main grid view (bottom-left or top-right corner)
- Or: Long-press gesture on "z rooms" title text
- Or: Three-finger tap gesture (hidden feature)

#### 3. Modified Files

**`TextToSpeechManager.swift`**:

**Changes Needed** (4 locations):
- Line 90: `startSpeakingCustomText(_:)`
- Line 149: `startSpeakingRandomMeditation()`
- Line 307: `startSpeakingWithPauses(_:)`
- Line 448: `speakWakeUpGreeting()`

**Modification Pattern**:
```swift
// Current code:
if let voice = AVSpeechSynthesisVoice(language: "en-US") {
    utterance.voice = voice
}

// New code:
utterance.voice = VoiceManager.shared.getPreferredVoice()
```

**`ContentView.swift`**:
- Add navigation to settings view
- No changes to TTS functionality (handled by TextToSpeechManager)

#### 4. Persistence

**UserDefaults Keys**:
- `useEnhancedVoice`: Bool (default: false)
- `preferredVoiceIdentifier`: String? (voice.identifier)
- `preferredVoiceLanguage`: String? (for display purposes)

#### 5. Fallback Logic

**Priority Order**:
1. User's selected enhanced/premium voice (if enabled and available)
2. Any downloaded enhanced voice for "en-US" (if enhanced setting is on)
3. Default compact system voice (current behavior)

**Implementation**:
```swift
func getPreferredVoice() -> AVSpeechSynthesisVoice? {
    guard useEnhancedVoice else {
        return AVSpeechSynthesisVoice(language: "en-US")
    }

    // Try user's preferred voice
    if let identifier = preferredVoiceIdentifier,
       let voice = AVSpeechSynthesisVoice(identifier: identifier) {
        return voice
    }

    // Fallback to any enhanced voice
    let enhancedVoices = AVSpeechSynthesisVoice.speechVoices()
        .filter { $0.language.hasPrefix("en-") &&
                  $0.quality != .default }

    if let voice = enhancedVoices.first {
        return voice
    }

    // Final fallback to default
    return AVSpeechSynthesisVoice(language: "en-US")
}
```

### User Experience Flow

**First-Time User**:
1. App works exactly as it does now (default voice)
2. User discovers settings icon/gesture
3. Opens voice settings, sees "Use Enhanced Voice" toggle (off)
4. Toggles on, sees list of available voices with quality indicators
5. Selects a voice → iOS automatically downloads if needed (system handles this)
6. Tests voice with preview button
7. Returns to app, meditation now uses enhanced voice

**System Voice Download**:
- iOS presents system download prompt when voice is first used
- Download happens in background
- If download fails/cancelled, app falls back to default voice
- Users can also pre-download voices via Settings → Accessibility → Spoken Content → Voices

### Implementation Estimate

**Complexity**: Low-Medium

**Estimated Changes**:
- New files: 2 (~300 lines total)
  - `VoiceManager.swift` (~150 lines)
  - `VoiceSettingsView.swift` (~150 lines)
- Modified files: 2
  - `TextToSpeechManager.swift` (~10 lines changed)
  - `ContentView.swift` (~30 lines for settings navigation)
- Total new/modified code: ~340 lines

**Development Time**: 4-6 hours

**Testing Requirements**:
- Test with no enhanced voices downloaded
- Test with multiple enhanced voices available
- Test voice download cancellation
- Test app behavior when preferred voice is deleted from system
- Test with VoiceOver enabled (accessibility)

---

## Android Implementation

### Technical Foundation

**Framework**: `android.speech.tts.TextToSpeech` (Android TTS API)

**Voice Quality Tiers**:
- **Standard voices** - Basic quality, pre-installed
- **High-quality voices** - Enhanced neural voices, downloadable via Google Play Services
- **WaveNet voices** (Google Cloud TTS) - Premium quality, requires internet + API billing

**Android Voice Sources**:
1. **System TTS Engine** (usually Google Text-to-Speech)
2. **Third-party TTS engines** (Samsung, etc.)
3. **Google Cloud Text-to-Speech API** (requires internet, not suitable for offline app)

### Feasibility Comparison

| Aspect | iOS | Android |
|--------|-----|---------|
| **Offline Voices** | ✅ Yes | ✅ Yes |
| **System-Managed Downloads** | ✅ Yes | ✅ Yes (via Google TTS app) |
| **App Size Impact** | ✅ None | ✅ None |
| **Free Enhanced Voices** | ✅ Yes | ⚠️ Limited |
| **API Complexity** | ✅ Simple | ⚠️ Moderate |
| **Voice Quality Control** | ✅ High | ⚠️ Variable (engine-dependent) |

### Architecture Changes (Android)

#### 1. Voice Management Module

**New File**: `VoiceManager.kt` or `VoiceManager.java`

**Key APIs**:
```kotlin
// Initialize TTS
textToSpeech = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        // Get available voices
        val voices = textToSpeech.voices

        // Filter for quality
        val enhancedVoices = voices.filter { voice ->
            voice.quality >= Voice.QUALITY_HIGH
        }
    }
}

// Voice properties
voice.name           // "en-us-x-sfg#male_1-local"
voice.locale         // Locale.US
voice.quality        // QUALITY_VERY_LOW to QUALITY_VERY_HIGH
voice.latency        // LATENCY_VERY_LOW to LATENCY_VERY_HIGH
voice.isNetworkConnectionRequired  // false for offline voices
```

#### 2. Settings UI

**New File**: `VoiceSettingsActivity.kt` or Fragment/Composable

**Components**:
- Toggle: "Use Enhanced Voice"
- Voice picker: Shows available high-quality voices
- Quality indicators: QUALITY_HIGH, QUALITY_VERY_HIGH, QUALITY_NORMAL
- Preview button: Test voice
- "Get More Voices" button: Deep-link to Google TTS app settings

**Deep-link to Google TTS**:
```kotlin
val intent = Intent()
intent.action = "com.android.settings.TTS_SETTINGS"
startActivity(intent)
```

#### 3. Voice Download Handling

**Challenge**: Unlike iOS, Android doesn't automatically prompt for voice downloads when a voice is selected. Users must manually download voices through the Google Text-to-Speech app.

**Solution**:
1. Detect when selected voice is not available
2. Show in-app dialog: "This voice requires download. Open Google Text-to-Speech settings?"
3. Provide button to open TTS settings
4. Show tutorial/help text with screenshots

**Code Example**:
```kotlin
fun checkVoiceAvailable(voice: Voice): Boolean {
    val result = textToSpeech.setVoice(voice)
    return result == TextToSpeech.SUCCESS
}

fun promptVoiceDownload(voiceName: String) {
    AlertDialog.Builder(context)
        .setTitle("Voice Download Required")
        .setMessage("$voiceName needs to be downloaded. Open voice settings?")
        .setPositiveButton("Open Settings") { _, _ ->
            openTTSSettings()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

#### 4. Modified Files

**`MeditationSpeechManager.kt`** (or equivalent):
- Replace hardcoded locale/voice with `VoiceManager.getPreferredVoice()`
- Add fallback handling for unavailable voices

**Modification Pattern**:
```kotlin
// Current code:
textToSpeech.language = Locale.US

// New code:
val voice = VoiceManager.getInstance().getPreferredVoice()
if (voice != null) {
    textToSpeech.setVoice(voice)
} else {
    textToSpeech.language = Locale.US  // Fallback
}
```

#### 5. Persistence

**SharedPreferences Keys**:
- `use_enhanced_voice`: Boolean (default: false)
- `preferred_voice_name`: String? (voice.name)
- `preferred_voice_locale`: String? (voice.locale.toString())

#### 6. Fallback Logic

**Priority Order**:
1. User's selected enhanced voice (if enabled and available)
2. Any QUALITY_HIGH or QUALITY_VERY_HIGH voice for current locale
3. Default system voice (current behavior)

### Android-Specific Challenges

**1. TTS Engine Variability**
- Different devices may have different TTS engines installed
- Voice availability varies by manufacturer (Samsung, Xiaomi, etc.)
- Quality standards not uniform across engines

**Solution**:
- Filter voices by Google TTS engine when possible
- Provide clear messaging about voice availability
- Robust fallback to system default

**2. Voice Naming Inconsistency**
- Voice names are often cryptic (e.g., "en-us-x-sfg#male_2-local")
- No friendly display names in API

**Solution**:
- Create mapping of common voice names to friendly names
- Display locale + quality level if friendly name unavailable
- Group voices by language/accent

**3. Manual Download Process**
- Users must leave app to download voices
- No programmatic download trigger

**Solution**:
- Excellent UX design for guiding users
- Consider in-app tutorial/help screen with screenshots
- Option to show "Available Now" vs. "Requires Download" sections

### Android Implementation Estimate

**Complexity**: Medium (higher than iOS due to API limitations)

**Estimated Changes**:
- New files: 3 (~500 lines total)
  - `VoiceManager.kt` (~200 lines)
  - `VoiceSettingsActivity.kt` (~200 lines)
  - `VoiceNameMapper.kt` (~100 lines - friendly name mappings)
- Modified files: 2-3
  - Meditation TTS manager (~20 lines)
  - Settings navigation (~40 lines)
  - Potentially MainActivity for settings launch
- Total new/modified code: ~560 lines

**Development Time**: 6-8 hours (extra time for UX polish around manual downloads)

**Testing Requirements**:
- Test on multiple Android versions (8.0+)
- Test with different TTS engines (Google, Samsung)
- Test with no Google TTS app installed
- Test voice unavailability scenarios
- Test TalkBack compatibility (accessibility)

---

## Comparative Summary

### iOS: **Highly Feasible** ✅

**Strengths**:
- Seamless voice download experience
- Consistent API across devices
- System manages downloads automatically
- No additional user education needed
- Clean, simple implementation

**Recommendation**: **Implement immediately** - straightforward enhancement with minimal risk

### Android: **Feasible with Caveats** ⚠️

**Strengths**:
- Offline voices available
- No app size impact
- Free enhanced voices exist

**Challenges**:
- Manual download process (UX friction)
- Voice availability varies by device
- Requires more user education
- More complex implementation

**Recommendation**: **Implement with enhanced UX guidance** - feasible but requires careful UX design around voice discovery and download process

---

## Cross-Platform Strategy

### Phase 1: iOS Implementation
- Simpler implementation
- Proves concept value
- Gather user feedback
- Refine settings UI/UX

### Phase 2: Android Implementation
- Apply learnings from iOS version
- Enhanced onboarding/tutorial for voice downloads
- Consider creating help documentation/video

### Phase 3: Iteration
- Monitor user adoption rates
- Collect feedback on voice preferences
- Consider adding voice packs/themes in future
- Potential for custom voice samples (advanced users)

---

## Design Recommendations

### Settings Screen Layout

**iOS & Android (Consistent Design)**:

```
┌─────────────────────────────────────┐
│  ← Voice Settings                   │
├─────────────────────────────────────┤
│                                     │
│  [🔊] Enhanced Voice        [○]     │
│  Use higher-quality meditation      │
│  voice (downloaded separately)      │
│                                     │
├─────────────────────────────────────┤
│                                     │
│  Voice Selection                    │
│  ┌───────────────────────────────┐ │
│  │ ◉ Samantha (US)      ✓ High  │ │
│  │ ○ Alex (US)          ✓ High  │ │
│  │ ○ Karen (AU)         ↓ Needs │ │
│  │                       Download│ │
│  └───────────────────────────────┘ │
│                                     │
│  [▶ Preview Voice]                  │
│                                     │
├─────────────────────────────────────┤
│                                     │
│  ℹ️ About Enhanced Voices            │
│  Higher quality voices are          │
│  downloaded by your device and      │
│  stored in system settings.         │
│  They do not increase app size.     │
│                                     │
│  [Get More Voices] (Android only)   │
│                                     │
└─────────────────────────────────────┘
```

### Meditation Text Preparation Note

**Current Implementation Compatibility**:
- Feature works seamlessly with existing pause system `(2s)`, `(1.5m)`
- No changes needed to 35 preset meditations
- Custom meditation editor remains unchanged
- Closed captioning continues to work identically
- Voice balance slider continues to function

**Voice Characteristics (iOS Implementation - December 2025)**:
- **Speech rate:** DYNAMIC based on voice quality
  - Default quality voices: 0.8x multiplier (slower, clearer for robotic voices)
  - Enhanced quality voices: 1.0x multiplier (natural speed for human-like voices)
  - Premium quality voices: 1.0x multiplier (natural speed for highest quality)
- **Pitch multiplier:** 1.0 for ALL voices (neutral, no artificial lowering)
  - Original 0.6 pitch created calming robotic effect for default voice
  - Same 0.6 pitch on enhanced voices sounded unnatural and strange
  - Standardizing to 1.0 allows each voice to use its natural tone
- **Implementation:** `VoiceManager.getSpeechRateMultiplier(for:)` returns appropriate multiplier
- **Rationale:** Default voices sound better slowed down, enhanced voices sound better at natural speed

---

## Maintenance Considerations

### iOS
- **OS Updates**: Apple occasionally adds new voices - minimal maintenance
- **API Stability**: AVSpeechSynthesizer is mature, stable API
- **Deprecation Risk**: Very low

### Android
- **TTS Engine Updates**: Google TTS app updates independently
- **API Stability**: Android TTS API stable since API 21
- **Fragmentation**: Some testing needed for new Android versions
- **Deprecation Risk**: Low (mature API)

---

## Alternative Approaches (Future Considerations)

### Custom Voice Integration
**Not recommended for v1** due to:
- Significant app size increase (200MB+ per voice)
- Licensing complexity
- Breaks offline-first, free model
- Development complexity much higher

### Third-Party TTS Services
**Not recommended** due to:
- Requires internet connection
- Subscription/API costs
- Against app philosophy
- Privacy concerns

### User-Recorded Voices
**Interesting future possibility**:
- Users record their own meditation voice
- Could be v2 or v3 feature
- Requires audio processing/normalization
- Storage considerations

---

## Success Metrics

**User Adoption**:
- % of users who enable enhanced voice
- % who download multiple voices
- Voice preference distribution

**Quality Metrics**:
- Meditation completion rates (enhanced vs. default)
- User ratings/reviews mentioning voice quality
- Support requests related to voice feature

**Technical Metrics**:
- Voice fallback frequency (indicates availability issues)
- Settings screen engagement
- Voice preview usage

---

## Conclusion

This feature is **highly feasible** on both platforms and aligns perfectly with the app's philosophy:

✅ Maintains free, offline, ad-free model
✅ Keeps app size under control
✅ Preserves default "artificial" aesthetic
✅ Gives power users professional quality
✅ Platform-native implementation
✅ Relatively simple to implement

**Recommendation**: Implement on iOS first (simpler), then Android with enhanced UX guidance.

---

## iOS Implementation Completed - December 25, 2025

### **Actual Implementation Summary**

**Status:** ✅ COMPLETE and TESTED

**Files Created:**
- `VoiceManager.swift` (Views/Components/) - 157 lines
- `VoiceSettingsView.swift` (Views/) - 340 lines

**Files Modified:**
- `TextToSpeechManager.swift` - 4 locations updated (lines 88-93, 145-150, 303-308, 443-448)
- `ContentView.swift` - No changes (gear icon removed for cleaner UI)
- `ExpandingView.swift` - Added gear icon + sheet presentation (leftmost button position)

**Total Code:** ~497 lines new, ~30 lines modified = ~527 lines total

### **Key Implementation Differences from Original Plan**

1. **Speech Rate is Dynamic, Not Fixed:**
   - **Original Plan:** 0.55x for all voices
   - **Actual Implementation:** 0.8x for default, 1.0x for enhanced/premium
   - **Reason:** Enhanced voices sounded unnatural when slowed to 0.55x
   - **Method:** `VoiceManager.getSpeechRateMultiplier(for:)` returns appropriate multiplier

2. **Pitch Multiplier Standardized to 1.0:**
   - **Original Plan:** 0.6 pitch for all voices
   - **Actual Implementation:** 1.0 pitch for all voices
   - **Reason:** 0.6 pitch sounded strange on enhanced voices
   - **Impact:** Each voice uses its natural tone

3. **Single Settings Access Point:**
   - **Original Plan:** Settings icon OR long-press OR three-finger tap (choose one)
   - **Actual Implementation:** Settings gear icon ONLY in ExpandingView (inside room view)
   - **Position:** Leftmost button in bottom row (gear → quote → clock → leaf)
   - **Reason:** Cleaner, more minimal main screen; settings are contextual to meditation experience

4. **Immediate Voice Preview Switching:**
   - **Original Plan:** Not specified
   - **Actual Implementation:** Clicking new voice preview immediately stops previous
   - **Reason:** Better UX - users can rapidly compare voices without waiting

5. **Info Section Text Refined:**
   - **Removed:** "They don't increase app size" (misleading - still uses device storage)
   - **Added:** Explicit storage estimates per voice quality level
   - **Added:** Path to iOS voice management settings

### **Voice Quality Configuration**

```swift
// VoiceManager.swift
func getSpeechRateMultiplier(for voice: AVSpeechSynthesisVoice?) -> Float {
    guard let voice = voice else {
        return 0.8  // Default for nil voice
    }

    // Enhanced and Premium voices sound better at normal speed
    if voice.quality == .enhanced || voice.quality == .premium {
        return 1.0
    }

    // Default quality voices need to be slowed down
    return 0.8
}
```

### **Settings UI Access**

**Single Access Point - Inside Room Only (ExpandingView):**
- Gear icon as leftmost button in bottom row (1st position)
- Button order: gear → quote (custom meditation) → clock (alarm timer) → leaf (meditation toggle)
- Same circular styling as other buttons
- Opens VoiceSettingsView as sheet
- **Design Rationale:** Cleaner, more minimal main screen; settings are contextual to meditation experience

### **Testing Results**

**Default Behavior:**
- ✅ Enhanced voice toggle OFF by default
- ✅ Default voice at 0.8x speed, 1.0 pitch
- ✅ No behavior change for users who don't enable feature

**Enhanced Voice Feature:**
- ✅ Toggle ON reveals voice list
- ✅ Quality badges show Default/Enhanced/Premium
- ✅ Download warnings show for non-default voices
- ✅ Preview plays sample meditation phrase at correct speed
- ✅ Multiple previews can be clicked rapidly (immediate switching)
- ✅ Selected voice persists across app restarts
- ✅ Meditations use selected voice at 1.0x speed
- ✅ Fallback to default works if selected voice unavailable

**UI/UX:**
- ✅ Settings accessible from inside room (leftmost button in bottom row)
- ✅ Main grid remains clean and minimal (no gear icon)
- ✅ Info section explains storage, system integration, iOS settings
- ✅ NavigationView with proper Done button
- ✅ ScrollView handles long voice lists

### **For Android Implementation**

When implementing this feature on Android, use the following adjusted specifications:

**Speech Rate:**
- Default quality voices: 0.8x multiplier
- High quality voices: 1.0x multiplier
- Very high quality voices: 1.0x multiplier

**Pitch:**
- All voices: 1.0 pitch multiplier (no artificial lowering)

**Preview Behavior:**
- Implement immediate switching (stop previous preview when new one starts)
- Use same speech rate logic as actual meditations

**Settings Access:**
- Add gear icon ONLY inside meditation view (inside room, not main grid)
- Position as leftmost button for easy discovery
- iOS implementation shows this creates cleaner main screen UX

**Info Section:**
- Remove any mention of "doesn't increase app size"
- Clarify that many voices come pre-installed on newer devices
- Note that some voices may require download (100-500MB each)
- Include storage estimates per quality level
- Provide path to Android TTS settings
- Explain how to delete voices to free up storage
- Add "Get More Voices" button with deep link to TTS settings

---

## Android Implementation Completed - December 25, 2025

### **Actual Implementation Summary**

**Status:** ✅ COMPLETE and TESTED

**Files Created:**
- `VoiceManager.kt` (package com.jmisabella.zrooms) - 335 lines
- `VoiceSettingsView.kt` (package com.jmisabella.zrooms) - 370 lines

**Files Modified:**
- `TextToSpeechManager.kt` - Added VoiceManager integration, dynamic voice selection, `applyVoiceSettings()`, `refreshVoiceSettings()`
- `ExpandingView.kt` - Added gear icon button (leftmost position), VoiceSettingsView sheet presentation

**Total Code:** ~705 lines new, ~30 lines modified = ~735 lines total

### **Key Implementation Highlights**

1. **Voice Discovery and Filtering:**
   - Discovers all available offline English voices via `TextToSpeech.voices`
   - Filters: `voice.locale.language == "en" && !voice.isNetworkConnectionRequired`
   - Sorts by quality (high to low), then US locale first
   - Only shows offline-capable voices (100% offline app requirement)

2. **Dynamic Speech Rate Implementation:**
   ```kotlin
   fun getSpeechRateMultiplier(voice: Voice?): Float {
       if (voice == null || !useEnhancedVoice.value) {
           return 0.8f  // DEFAULT_VOICE_RATE
       }
       return if (voice.quality >= Voice.QUALITY_HIGH) {
           1.0f  // ENHANCED_VOICE_RATE
       } else {
           0.8f
       }
   }
   ```
   - Default voices (QUALITY_NORMAL): 0.8x speed
   - Enhanced voices (QUALITY_HIGH, QUALITY_VERY_HIGH): 1.0x speed
   - Matches iOS implementation approach

3. **Pitch Standardization:**
   - All voices use 1.0 pitch (natural tone)
   - Previous code used 0.58 pitch - removed for better quality
   - Constant: `MEDITATION_PITCH = 1.0f`

4. **Friendly Voice Name Mapping:**
   - Android voice names are cryptic: "en-us-x-tpf-local"
   - Extracts voice code: "tpf" from "-x-tpf-"
   - Maps to iOS-like names based on actual Google TTS voice characteristics:
     - Female: sfg→Samantha, iob→Emily, iog→Victoria
     - Male: tpf→Alex, iom→Daniel, tpd→Michael, tpc→James
   - Fallback: Voice A, Voice B, Voice C... for unknown codes

5. **Preview Management:**
   - Separate `previewTTS` instance to avoid interfering with meditation playback
   - `stopPreview()` called before starting new preview
   - Immediate switching: clicking new voice stops previous preview instantly
   - Uses same speech rate as actual meditations for accurate preview

6. **Settings UI Access:**
   - Gear icon in ExpandingView (meditation view) as leftmost button
   - Button order: gear → quote → clock → leaf
   - Full-screen Compose sheet overlay
   - `refreshVoiceSettings()` called on dismiss to apply changes

7. **Voice Settings UI (Jetpack Compose):**
   - Enhanced Voice toggle (OFF by default)
   - LazyColumn scrollable voice list (only visible when toggle ON)
   - VoiceListItem components:
     - Friendly name (no redundant quality label)
     - Locale display
     - "Needs Download" indicator for unavailable voices
     - Preview play/stop button
     - Checkmark for selected voice
   - "Get More Voices" button: Deep-links to `com.android.settings.TTS_SETTINGS`
   - Info section at bottom of scrollable list
   - Close button with "X" icon

8. **Persistence:**
   - SharedPreferences keys:
     - `PREF_USE_ENHANCED_VOICE` (Boolean, default: false)
     - `PREF_PREFERRED_VOICE_NAME` (String, voice.name)
   - Survives app restarts and device reboots
   - Restored on app initialization

9. **Fallback Logic:**
   - Priority: User's selected voice → Any QUALITY_HIGH voice → Default system voice
   - Handles case where selected voice deleted from system
   - Always falls back to working voice

### **Android-Specific Challenges Resolved**

1. **Manual Voice Download:**
   - Unlike iOS (automatic), Android requires manual download via Google TTS app
   - Solution: "Get More Voices" button with deep-link to TTS settings
   - Info section explains download process step-by-step
   - "Needs Download" indicators on unavailable voices

2. **Voice Name Mapping:**
   - Android voice names cryptic (e.g., "en-us-x-sfg#female_1-local")
   - Solution: Extract voice code and map to friendly names
   - Based on actual Google TTS voice code documentation
   - Testing confirmed correct gender matching

3. **Preview Audio Looping:**
   - Initial implementation: preview kept looping
   - Solution: Separate `previewTTS` instance with `stopPreview()` method
   - Proper cleanup in `onDone` and `onError` callbacks

4. **Voice List UI:**
   - Initial implementation: info section took up half screen
   - Solution: Moved info section to bottom of LazyColumn (scrollable)
   - Quality label removed from names (redundant in Enhanced Voice context)

### **Testing Results**

**Default Behavior:**
- ✅ Enhanced voice toggle OFF by default
- ✅ Default voice at 0.8x speed, 1.0 pitch
- ✅ No behavior change for users who don't enable feature

**Enhanced Voice Feature:**
- ✅ Toggle ON reveals scrollable voice list
- ✅ Friendly names display correctly (Samantha, Alex, etc.)
- ✅ Voice names match actual voice gender
- ✅ Preview plays at correct speed (1.0x for enhanced, 0.8x for default)
- ✅ Multiple previews can be clicked rapidly (immediate switching)
- ✅ Selected voice persists across app restarts
- ✅ Meditations use selected voice at correct speed
- ✅ Fallback to default works when selected voice unavailable
- ✅ "Needs Download" indicator shows for unavailable voices
- ✅ "Get More Voices" button opens Android TTS settings

**UI/UX:**
- ✅ Settings accessible from meditation view (gear icon, leftmost button)
- ✅ Main grid remains clean (no gear icon)
- ✅ Info section at bottom of scrollable list
- ✅ Close button dismisses sheet and refreshes settings
- ✅ LazyColumn handles long voice lists smoothly

### **Key Differences from iOS Implementation**

| Aspect | iOS | Android |
|--------|-----|---------|
| **Voice Download** | Automatic on first use | Manual via TTS settings |
| **Voice Names** | Built-in (Samantha, Alex, etc.) | Custom mapping required |
| **Settings Access** | Gear in ExpandingView | Gear in ExpandingView |
| **UI Framework** | SwiftUI | Jetpack Compose |
| **Persistence** | UserDefaults | SharedPreferences |
| **Voice Quality API** | `.default`, `.enhanced`, `.premium` | `QUALITY_NORMAL`, `QUALITY_HIGH`, `QUALITY_VERY_HIGH` |
| **Speech Rate** | 0.8x default, 1.0x enhanced | 0.8x default, 1.0x enhanced |
| **Pitch** | 1.0 for all | 1.0 for all |

### **Files Reference**

**VoiceManager.kt** (335 lines):
- Singleton pattern with `getInstance(context)`
- `discoverVoices()`: Filters offline English voices, sorts by quality
- `getFriendlyVoiceName(voice)`: Maps voice codes to friendly names
- `getSpeechRateMultiplier(voice)`: Returns 0.8x or 1.0x based on quality
- `getPreferredVoice()`: Returns user's selected voice with fallback logic
- `previewVoice(voice, text, onComplete)`: Previews voice with sample text
- `stopPreview()`: Stops ongoing preview
- `setUseEnhancedVoice(enabled)`: Toggles enhanced voice feature
- `setPreferredVoice(voice)`: Saves voice preference
- Observable state: `availableVoices`, `useEnhancedVoice`, `selectedVoice`

**VoiceSettingsView.kt** (370 lines):
- Compose full-screen settings UI
- Enhanced Voice toggle with explanation text
- LazyColumn with voice list items
- VoiceListItem composable: name, locale, preview button, selection indicator
- "Get More Voices" button
- Info section at bottom (scrollable)
- Close button in header

**TextToSpeechManager.kt** (MODIFIED):
- Added `voiceManager` instance
- `applyVoiceSettings()`: Sets voice and dynamic speech rate
- `refreshVoiceSettings()`: Re-applies settings (called when user changes preferences)
- Removed hardcoded `MEDITATION_RATE` constant
- Uses `MEDITATION_PITCH = 1.0f` constant

**ExpandingView.kt** (MODIFIED):
- Added `voiceManager` instance
- Added `showVoiceSettings` state variable
- Gear icon button (leftmost position in bottom row)
- VoiceSettingsView sheet presentation
- Calls `ttsManager.refreshVoiceSettings()` on dismiss

### **Implementation Matches Specifications**

All Android implementation specifications from the "For Android Implementation" section above were successfully implemented:

✅ Speech rate: 0.8x for default, 1.0x for enhanced
✅ Pitch: 1.0 for all voices
✅ Preview behavior: Immediate switching
✅ Settings access: Gear icon in meditation view (leftmost button)
✅ Info section: No "doesn't increase app size" text
✅ Info section: Pre-installed voices mentioned
✅ Info section: Download requirements noted (100-500MB)
✅ Info section: Path to Android TTS settings provided
✅ "Get More Voices" button: Deep-link to TTS settings

### **Cross-Platform Summary**

Both iOS and Android implementations are now **COMPLETE** and follow the same design patterns:

- Optional enhanced voice feature (OFF by default)
- Gear icon in meditation view (inside room, not main grid)
- Dynamic speech rate based on voice quality
- Natural pitch (1.0) for all voices
- Voice preview with immediate switching
- Persistent preferences
- Robust fallback logic
- Offline-only voices
- 100% free, no subscriptions, no internet required

The feature maintains the app's core philosophy while providing power users with professional-quality voice options.
