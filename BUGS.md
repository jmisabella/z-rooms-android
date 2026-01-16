# Known Bugs and Fixes

This document tracks bugs discovered and their resolutions.

## Fixed Bugs

### Voice Settings Not Applied to Guided Storys (December 26, 2024)

**Description:**
When a user selected a different voice in the Voice Settings dialog, the selected voice was not being applied to guided storys. The story would continue using the default system voice instead of the user's chosen voice.

**Root Cause:**
The `VoiceManager.setPreferredVoice()` method was saving the voice selection to preferences, but was not enabling the `useEnhancedVoice` flag. The `getPreferredVoice()` method checks if enhanced voice is enabled, and returns `null` (default system voice) when it's disabled, even if a voice has been selected.

**Impact:**
- Users could select enhanced voices but they would not be used
- Voice previews worked correctly, but actual story playback ignored the selection
- User experience was confusing as the settings appeared to save but had no effect

**Fix:**
Modified `VoiceManager.setPreferredVoice()` to automatically enable `useEnhancedVoice` when a voice is selected. This ensures that:
1. When user selects a voice, the enhanced voice feature is automatically enabled
2. The selected voice is properly saved to SharedPreferences
3. Both preferences are updated atomically in a single transaction

**Files Changed:**
- `app/src/main/java/com/jmisabella/zrooms/VoiceManager.kt`

**Code Changes:**
```kotlin
// Before:
fun setPreferredVoice(voice: Voice?) {
    selectedVoice.value = voice
    if (voice != null) {
        prefs.edit().putString(PREF_PREFERRED_VOICE_NAME, voice.name).apply()
    } else {
        prefs.edit().remove(PREF_PREFERRED_VOICE_NAME).apply()
    }
}

// After:
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

**Testing:**
To verify the fix:
1. Open a room
2. Tap the Voice Settings button (gear icon)
3. Select a different voice from the list
4. Close the Voice Settings dialog
5. Tap the story button (leaf icon) to play a guided story
6. Verify the story uses the selected voice

**Status:** Fixed in version ready for Play Store deployment
