# Summary of TTS and Content Selection Changes for zz-time App

## Overview
This document summarizes the changes made to the iOS version of the zz-time app on January 13, 2026, to improve text-to-speech (TTS) naturalness and content selection behavior. These changes are intended to be ported to the Android version of the app.

## Changes Made

### 1. TTS Pause Adjustments for Natural Flow
**Problem:** TTS speech sounded artificial with excessive pauses after sentences and periods.

**Changes:**
- **Sentence Pausing:** Initially added 0.5-second pauses after sentences, then reverted to no automatic sentence pauses to avoid artificial breaks.
- **Period Pausing:** Removed excessive pauses after periods (initially 1 second after every period).
- **Paragraph Pausing:** Reduced pauses between paragraphs from 4 seconds to 2 seconds for better flow while maintaining structural breaks.

**Implementation Details:**
- Modified `addAutomaticPauses` function in `TextToSpeechManager.swift`
- Pauses are now only added between paragraphs (2 seconds) and manually specified pauses (e.g., "(2s)")
- No automatic pauses after sentences or periods

**Result:** TTS flows more naturally, with appropriate breaks only at paragraph boundaries.

### 2. Question Mark Preservation in TTS
**Problem:** Question marks were intentionally stripped from TTS text to prevent rising intonation, but this made questions sound unnatural in story content.

**Changes:**
- Removed the code that replaced "?" with "" in the TTS processing pipeline.
- Question marks are now preserved in both spoken text and closed captions.

**Implementation Details:**
- Modified `startSpeakingWithPauses` function in `TextToSpeechManager.swift`
- Removed: `let textWithoutQuestions = text.replacingOccurrences(of: "?", with: "")`
- Closed captions automatically display question marks since they use the same processed text

**Result:** Questions are pronounced with proper rising intonation, making stories sound more natural.

### 3. Preset-Only Random Selection for Leaf and Poetry Buttons
**Problem:** The Leaf (story) and Poetry buttons randomly selected from both preset and custom content, diluting the curated experience.

**Changes:**
- Modified random selection functions to only include preset content.
- Custom storys and poems are excluded from random play via buttons.

**Implementation Details:**
- Updated `getRandomStory()` and `getRandomPoem()` in `TextToSpeechManager.swift`
- Removed code that added custom content to the selection pool
- Custom content remains accessible through dedicated list views
- Updated comments in `ExpandingView.swift` to reflect preset-only behavior

**Result:** Leaf and Poetry buttons now provide a consistent, curated experience with only preset content.

### 4. Sequential Story Chapter Playback for Leaf Button
**Problem:** The Leaf button randomly selected preset storys, but for story mode, it should play chapters sequentially with progress tracking and navigation controls.

**Changes:**
- Replaced random selection with sequential playback starting from chapter 1 (preset_story1.txt).
- Added persistent progress tracking using UserDefaults to remember current chapter across sessions.
- Implemented skip back/forward controls (< > triangles) that appear on screen sides only when Leaf mode is active.
- Auto-advances to next chapter after completing a session.

**Implementation Details:**
- Added `currentChapterIndex` property with `@AppStorage` persistence in `TextToSpeechManager.swift`
- Replaced `getRandomStory()` with `getSequentialStory()` to load current chapter
- Added `skipToNextChapter()` and `skipToPreviousChapter()` methods with bounds checking
- Updated `didFinishSpeaking()` to auto-advance chapters on completion
- Added conditional UI elements in `ExpandingView.swift` for skip controls

**Result:** Users can now experience stories as sequential chapters with full navigation, progress persistence, and intuitive controls.

### 5. Improved Skip Button UI Positioning and Size
**Problem:** The skip back/forward buttons for story navigation were poorly positioned (left button appeared centered, buttons vertically centered on screen) and oversized, creating a bad visual experience.

**Changes:**
- Repositioned buttons immediately below the closed caption box instead of above main buttons.
- Fixed layout to place left button on left screen edge, right button on right edge.
- Reduced button size by changing font from .title to .title2 and padding from 20 to 10.

**Implementation Details:**
- Modified `ExpandingView.swift` HStack layout from Spacer/Button/Spacer/Button/Spacer to Button/Spacer/Button with .padding(.horizontal, 20)
- Adjusted .padding(.bottom) from 200 to 120 to position below captions
- Reduced button styling for smaller appearance

**Result:** Skip buttons are now properly positioned, smaller, and visually balanced. (Note: This is iOS-specific UI; Android may need equivalent layout adjustments for similar controls.)

## Files Modified
- `TextToSpeechManager.swift`: Core TTS processing changes
- `ExpandingView.swift`: Comments updated for content selection
- `CHANGE_LOG.md`: Documentation of all changes

## Important Notes for Android Port
1. **TTS Engine Differences:** Android's TextToSpeech API may handle punctuation differently than iOS AVSpeechSynthesizer. Test question mark intonation carefully.
2. **Pause Implementation:** Android TTS pause insertion might require different approaches (e.g., using SSML markup or manual utterance splitting).
3. **Content Management:** Ensure Android has equivalent preset content loading and custom content exclusion logic.
4. **Closed Captions:** Android may need explicit caption handling if TTS text processing differs from display text.
5. **Testing:** Verify TTS naturalness with various voices, especially premium ones, and ensure pauses feel appropriate for story content.

## Testing Recommendations
- Test TTS with content containing questions to confirm rising intonation
- Verify paragraph breaks feel natural (2 seconds)
- Confirm Leaf/Poetry buttons only play presets
- Test with different TTS voices and speeds
- Check closed caption display includes question marks

## Additional Context
These changes shift the app's focus toward narrative/story content while maintaining the meditative experience through curated presets. The TTS improvements make stories more engaging, while the content selection ensures consistent quality.</content>
<parameter name="filePath">/Users/jeffrey/Documents/Git/zz-time/TTS_Changes_Summary.md