# Android Implementation Guide: Hide Closed Caption Box When Narration Ends

## Overview
This guide describes how to fix a UI bug where the closed caption box remains visible after meditation/poetry narration completes, even though there's no text being spoken.

## Problem Description
In the Z Rooms app, when meditation or poetry mode narration completes:
- The text-to-speech voice stops speaking
- The caption text variables are cleared (set to empty strings)
- **BUT** the dark background box for captions remains visible on screen
- This looks unprofessional and confusing to users

## Root Cause
The closed caption UI component unconditionally renders the dark background container, regardless of whether there's any text to display. When narration ends and both the current phrase and previous phrase are cleared to empty strings, the empty dark box persists on screen.

## iOS Solution (Reference)
On iOS, the fix was implemented in `MeditationTextDisplay.swift` by wrapping the entire caption box component in a conditional check:

```swift
var body: some View {
    // Only show the caption box if there's text to display
    if !currentPhrase.isEmpty || !previousPhrase.isEmpty {
        ZStack {
            // Dark background rectangle
            // Current phrase text
            // Previous phrase text
        }
    }
}
```

**Key principle**: The component only renders when `currentPhrase` OR `previousPhrase` contains text.

## Android Implementation Steps

### 1. Locate the Closed Caption UI Component
Find the composable/view/layout that displays closed captions. This component likely:
- Shows a dark semi-transparent background box
- Displays current phrase text (likely in white, medium/large font)
- May display previous phrase text (likely in white with reduced opacity)
- Is positioned near the bottom of the screen during meditation/poetry playback

**Look for:**
- Jetpack Compose: A `@Composable` function that displays caption text
- XML Views: A layout file with TextViews for captions and a dark background
- Custom View: A class extending View that draws caption text

### 2. Identify the Text State Variables
Find where the caption text is stored and updated. Look for:
- `currentPhrase` / `current_phrase` / `currentText` (or similar naming)
- `previousPhrase` / `previous_phrase` / `previousText` (or similar naming)
- These are likely:
  - LiveData/StateFlow/MutableState variables in a ViewModel
  - Observable properties updated by the text-to-speech manager
  - String variables that get updated as narration progresses

### 3. Understand the Current Display Logic
Check how the caption box is currently shown/hidden:
- Is there already any visibility logic?
- Does it check if meditation/poetry is playing?
- Does it respect a user preference for showing/hiding captions?

### 4. Implement the Conditional Rendering

#### For Jetpack Compose:
Add a conditional check that wraps the entire caption box component:

```kotlin
@Composable
fun MeditationTextDisplay(
    currentPhrase: String,
    previousPhrase: String
) {
    // Only show the caption box if there's text to display
    if (currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Previous phrase (faded)
                if (previousPhrase.isNotEmpty()) {
                    Text(
                        text = previousPhrase,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                // Current phrase (full brightness)
                if (currentPhrase.isNotEmpty()) {
                    Text(
                        text = currentPhrase,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
```

#### For XML Views:
Update the visibility logic in your Activity/Fragment:

```kotlin
private fun updateCaptionVisibility() {
    val currentPhrase = viewModel.currentPhrase.value ?: ""
    val previousPhrase = viewModel.previousPhrase.value ?: ""

    // Only show caption box if there's text to display
    if (currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty()) {
        captionBoxContainer.visibility = View.VISIBLE

        // Update individual text views
        if (previousPhrase.isNotEmpty()) {
            previousPhraseText.visibility = View.VISIBLE
            previousPhraseText.text = previousPhrase
        } else {
            previousPhraseText.visibility = View.GONE
        }

        if (currentPhrase.isNotEmpty()) {
            currentPhraseText.visibility = View.VISIBLE
            currentPhraseText.text = currentPhrase
        } else {
            currentPhraseText.visibility = View.GONE
        }
    } else {
        // Hide entire caption box when both phrases are empty
        captionBoxContainer.visibility = View.GONE
    }
}
```

### 5. Verify Text Clearing on Completion
Ensure your text-to-speech manager clears both phrase variables when narration completes:

```kotlin
private fun onNarrationComplete() {
    // Clear caption text
    _currentPhrase.value = ""
    _previousPhrase.value = ""

    // Update playback state
    _isPlayingMeditation.value = false

    // Mark completion for wake-up greeting feature
    sharedPrefs.edit().putBoolean("contentCompletedSuccessfully", true).apply()
}
```

### 6. Preserve Existing Features
Make sure the fix doesn't break:
- User preference toggle for showing/hiding captions
- Smooth fade-in/fade-out animations
- Caption display during active narration
- Wake-up greeting functionality

## Testing Checklist
After implementing the fix, test:

1. ✅ Start meditation/poetry with captions enabled
2. ✅ Verify captions display correctly during narration
3. ✅ Verify previous phrase fades, current phrase is bright
4. ✅ Wait for narration to complete
5. ✅ **Verify the dark caption box disappears when narration ends**
6. ✅ Test with captions disabled - should never show box
7. ✅ Test rapid start/stop of meditation - box should hide/show cleanly
8. ✅ Test wake-up greeting still works after meditation completes

## Expected Behavior After Fix

### Before Fix:
- Narration ends → Text disappears → Empty dark box remains visible ❌

### After Fix:
- Narration ends → Text disappears → Entire caption box (including background) disappears ✅

## Files to Update
Based on the iOS implementation, you'll likely need to modify:

1. **Caption UI Component** - Add conditional rendering logic
2. **CHANGE_LOG.md** (or equivalent) - Document the bug fix

## Additional Notes

- This is a **display-only fix** - no changes to text-to-speech logic needed
- The fix is purely cosmetic but improves user experience
- Maintains backward compatibility with all existing features
- No performance impact - actually slightly more efficient

## Questions to Answer While Implementing

1. What is the exact component/file name for the caption display?
2. Are you using Jetpack Compose or XML Views?
3. Where are `currentPhrase` and `previousPhrase` stored (ViewModel, Manager class)?
4. Is there already a user preference for showing/hiding captions?
5. Are there any animations that need to be preserved?

## iOS Reference Files
For detailed reference, see the iOS implementation:
- `MeditationTextDisplay.swift` - Caption UI component (lines 8-40)
- `TextToSpeechManager.swift` - Text clearing on completion (lines 742-747)
- `ExpandingView.swift` - Integration point (lines 369-382)
- `CHANGE_LOG.md` - Documentation of the fix

---

**Summary**: Add a single conditional check `if (currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty())` around the entire caption box component to hide it when both phrases are empty.
