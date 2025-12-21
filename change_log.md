# Z Rooms Android - Change Log

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
