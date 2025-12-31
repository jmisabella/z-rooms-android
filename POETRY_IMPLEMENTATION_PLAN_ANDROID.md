# Poetry Feature Implementation Plan - Z Rooms Android

**Project**: Z Rooms Android App
**Feature**: Add poetry functionality alongside meditation with 3-state toggle
**Date**: December 30, 2024
**Status**: Ready for Implementation
**Based on**: iOS implementation (POETRY_IMPLEMENTATION_GUIDE.md v1.1)

---

## Table of Contents
1. [Overview](#overview)
2. [User Decisions](#user-decisions)
3. [Architecture Summary](#architecture-summary)
4. [Implementation Phases](#implementation-phases)
5. [Detailed File Changes](#detailed-file-changes)
6. [Testing Checklist](#testing-checklist)
7. [Implementation Order](#implementation-order)

---

## Overview

### Feature Description
Transform the meditation app to support both guided meditations and poetry readings. The Leaf button becomes a 3-state toggle:
- **State 1**: Off (gray leaf icon - `Icons.Outlined.Eco`)
- **State 2**: Meditation mode (green leaf icon - `Icons.Outlined.Eco` with green color)
- **State 3**: Poetry mode (purple theater masks icon - `Icons.Filled.TheaterComedy`)

### Key Requirements
✅ 3-state toggle button cycle (Off → Meditation → Poetry → Off)
✅ Mode persists across app sessions and room changes
✅ Unified content browser with tabs for "Meditations" and "Poems"
✅ Exactly 4 buttons remain in ExpandingView (no new buttons)
✅ Wake greeting works for both content types
✅ 1-2 second crossfade when switching between modes
✅ Animated loading indicator during meditation→poetry transition
✅ Zero migration issues for existing users

### Design Principles
- **Parallel Architecture**: Create separate poem structures alongside meditation (don't merge)
- **Code Reuse**: Leverage existing TTS infrastructure and pause syntax
- **Clean UI**: Keep existing 4-button layout, use tabbed interface for content
- **Incremental Content**: Start with 2 placeholder poems, add more later

---

## User Decisions

Based on clarifying questions, the user selected:

✅ **Icon choice**: Material Icons theater masks (Icons.Filled.TheaterComedy / Icons.Outlined.TheaterComedy)
- Quick to implement, consistent with other Material icons in the app

✅ **Loading indicator**: Yes, include pulsing dots during meditation→poetry transition
- Show a pulsing three-dot icon during crossfade to prevent user confusion
- Matches the improved iOS UX from v1.1

✅ **UI approach**: Tabbed content browser
- Single Quote button opens a dialog with tabs for Meditations/Poems
- Matches iOS design and keeps the 4-button layout

✅ **Custom editor**: Single editor with type selector
- One editor with a Meditation/Poem toggle at the top
- Simpler UX and matches user's description in the original request

---

## Architecture Summary

### New Components
```
Models/
  ├─ ContentMode.kt                    [NEW] Enum: OFF/MEDITATION/POETRY
  └─ CustomPoem.kt                     [NEW] Poem data model

Managers/
  └─ CustomPoetryManager.kt            [NEW] Poem CRUD & persistence

Views/
  ├─ ContentBrowserView.kt             [NEW] Tabbed interface (Meditations | Poems)
  ├─ CustomPoemListView.kt             [NEW] Browse/manage poems
  └─ LoadingDotsIndicator.kt           [NEW] 3-dot pulsing animation

res/raw/                               [NEW DIRECTORY]
  ├─ default_custom_poem.txt           [NEW] Placeholder default poem
  └─ preset_poem1.txt ... preset_poem35.txt  [NEW] 35 preset poems
```

### Modified Components
```
Managers/
  └─ TextToSpeechManager.kt            [MODIFY] Add poetry support, content mode

Views/
  ├─ ExpandingView.kt                  [MODIFY] 3-state toggle, ContentBrowserView
  ├─ CustomMeditationListView.kt       [MODIFY] Extract content component
  └─ AudioService.kt                   [MODIFY] Wake greeting flag rename
```

### State Management
```
ContentMode Enum:
  - OFF: Nothing playing
  - MEDITATION: Random meditation playing (green leaf)
  - POETRY: Random poem playing (purple theater masks)

SharedPreferences Keys:
  - "custom_poems": JSON array of CustomPoem objects
  - "contentMode": Current session mode (OFF/MEDITATION/POETRY)
  - "contentCompletedSuccessfully": Wake greeting flag (replaces "meditationCompletedSuccessfully")
```

---

## Implementation Phases

### Phase 1: Data Models & Core Infrastructure (Day 1)

**Goal**: Create core data structures and poem storage

**Tasks**:
1. Create `ContentMode.kt` enum
2. Create `CustomPoem.kt` data class
3. Create `CustomPoetryManager.kt` manager
4. Create `default_custom_poem.txt` resource file
5. Test: Verify poetry manager CRUD operations work

**Estimated Effort**: 2-3 hours

---

### Phase 2: TextToSpeechManager Updates (Day 1-2)

**Goal**: Add poetry playback support and content mode cycling

**Tasks**:
1. Replace `isPlayingMeditation` with `contentMode: ContentMode` property
2. Add `isLoading` state for loading indicator
3. Add `customPoetryManager` parameter to constructor
4. Create `loadRandomPoemFile()` method (mirrors `loadRandomMeditationFile()`)
5. Create `startSpeakingRandomPoem()` method
6. Create `cycleContentMode()` method with loading state
7. Create `saveContentMode()` and `restoreContentMode()` methods
8. Update wake greeting flag: `PREF_MEDITATION_COMPLETED` → `PREF_CONTENT_COMPLETED`
9. Test: Verify mode cycling, loading states, persistence

**File**: `app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt`
**Estimated Effort**: 3-4 hours

---

### Phase 3: UI Integration - ExpandingView (Day 2-3)

**Goal**: Implement 3-state toggle button and integrate ContentBrowserView

**Tasks**:
1. Add `poetryManager = CustomPoetryManager(context)`
2. Update TTS manager construction to include poetry manager
3. Add `scope = rememberCoroutineScope()`
4. Replace `isMeditationPlaying` with `contentMode` and `isLoading` states
5. Rename `showMeditationList` → `showContentBrowser`
6. Update state tracking with `LaunchedEffect`
7. Create helper functions for icon/color based on content mode (or inline)
8. Update Leaf/Masks button:
   - Show loading indicator when `isLoading == true`
   - Show appropriate icon based on `contentMode`
   - Apply appropriate color (gray/green/purple)
   - Update click handler to call `cycleContentMode()`
9. Replace meditation list sheet with ContentBrowserView sheet
10. Update MeditationTextDisplay visibility condition
11. Test: Verify button cycling, icons, colors, loading animation

**File**: `app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt`
**Estimated Effort**: 3-4 hours

---

### Phase 4: Content Management Views (Day 3-4)

**Goal**: Create unified content browser with tabs

**Tasks**:
1. Create `LoadingDotsIndicator.kt` - 3-dot pulsing animation component
2. Create `ContentBrowserView.kt`:
   - Tabbed interface with "Meditations" | "Poems" tabs
   - Tab selection state
   - Delegates to `CustomMeditationListContent` or `CustomPoemListContent`
3. Modify `CustomMeditationListView.kt`:
   - Extract content into `CustomMeditationListContent()` composable
   - Keep original `CustomMeditationListView()` for backward compatibility
4. Create `CustomPoemListView.kt`:
   - Copy `CustomMeditationListView.kt`
   - Replace: Meditation→Poem, green→purple, FormatQuote→TheaterComedy
   - Implement `CustomPoemListContent()` and `CustomPoemListView()`
5. Test: Verify tab navigation, content display, editing

**Estimated Effort**: 4-5 hours

---

### Phase 5: Wake Greeting Update (Day 4)

**Goal**: Make wake greeting work for both content types

**Tasks**:
1. Update `AudioService.kt`:
   - Rename variable: `meditationCompleted` → `contentCompleted`
   - Change flag: `PREF_MEDITATION_COMPLETED` → `PREF_CONTENT_COMPLETED`
2. Test: Verify greeting plays after meditation completion
3. Test: Verify greeting plays after poetry completion

**File**: `app/src/main/java/com/jmisabella/zrooms/AudioService.kt`
**Estimated Effort**: 30 minutes

---

### Phase 6: Content Assets (Day 5)

**Goal**: Create poem content files

**Tasks**:
1. Create `default_custom_poem.txt` (default fallback poem)
2. Create 35 preset poem files (`preset_poem1.txt` through `preset_poem35.txt`)
3. Add pause markers for natural reading rhythm
4. Test: Verify all poems load and play correctly

**Location**: `app/src/main/res/raw/`
**Estimated Effort**: 3-4 hours (includes content curation)

---

### Phase 7: Testing & Validation (Day 5-6)

**Goal**: Verify all functionality works correctly

**Tasks**: See [Testing Checklist](#testing-checklist) below

**Estimated Effort**: 2-3 hours

---

## Detailed File Changes

### 1. Create `ContentMode.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/ContentMode.kt` (~15 lines)

```kotlin
package com.jmisabella.zrooms

enum class ContentMode {
    OFF,
    MEDITATION,  // Green leaf icon
    POETRY       // Purple theater masks icon
}
```

**Notes**: Simple enum for tri-state toggle management.

---

### 2. Create `CustomPoem.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/CustomPoem.kt` (~14 lines)

```kotlin
package com.jmisabella.zrooms

import java.util.Date
import java.util.UUID

data class CustomPoem(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var text: String,
    val dateCreated: Date = Date()
)
```

**Notes**: Identical structure to `CustomMeditation.kt` for consistency.

---

### 3. Create `CustomPoetryManager.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/CustomPoetryManager.kt` (~120 lines)

**Pattern**: Mirror `CustomMeditationManager.kt` exactly with these changes:

| Original | Change To |
|----------|-----------|
| `CustomMeditation` | `CustomPoem` |
| `meditations` | `poems` |
| `"custom_meditations"` | `"custom_poems"` |
| `"customMeditations"` | `"customPoems"` |
| `"default_custom_meditation"` | `"default_custom_poem"` |
| `addMeditation()` | `addPoem()` |
| `updateMeditation()` | `updatePoem()` |
| `deleteMeditation()` | `deletePoem()` |
| `duplicateMeditation()` | `duplicatePoem()` |
| `getRandomMeditation()` | `getRandomPoem()` |

**Key Methods**:
- `loadPoems()` - Load from SharedPreferences, restore default if empty
- `savePoems()` - JSON encode and persist
- `addPoem()`, `updatePoem()`, `deletePoem()`, `duplicatePoem()` - CRUD operations
- `getRandomPoem()` - Returns random poem

---

### 4. Update `TextToSpeechManager.kt`

**File**: `app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt`

#### A. Update Constructor (~line 20-23)
```kotlin
// CHANGE FROM:
class TextToSpeechManager(
    private val context: Context,
    private val customMeditationManager: CustomMeditationManager? = null
) {

// TO:
class TextToSpeechManager(
    private val context: Context,
    private val customMeditationManager: CustomMeditationManager? = null,
    private val customPoetryManager: CustomPoetryManager? = null
) {
```

#### B. Replace State Variables (~line 27-28)
```kotlin
// REPLACE:
var isPlayingMeditation by mutableStateOf(false)
    private set

// WITH:
var contentMode by mutableStateOf(ContentMode.OFF)
    private set

var isLoading by mutableStateOf(false)  // For 3-dot pulsing indicator
    private set
```

#### C. Add New Variables (~line 46)
```kotlin
// ADD after lastPlayedMeditation:
private var lastPlayedPoem: String? = null
```

#### D. Update SharedPreferences Constants (~line 56)
```kotlin
// ADD to companion object:
const val PREF_CONTENT_MODE = "contentMode"
const val PREF_CONTENT_COMPLETED = "contentCompletedSuccessfully"
```

#### E. Add Poetry Loading Method
Add after `loadRandomMeditationFile()` method:

```kotlin
/**
 * Loads a random poem from both preset files and custom poems
 */
private fun loadRandomPoemFile(): String? {
    val allPoems = mutableListOf<String>()

    // 1. Load all preset poem files (preset_poem1 through preset_poem35)
    var i = 1
    while (true) {
        val resId = context.resources.getIdentifier(
            "preset_poem$i",
            "raw",
            context.packageName
        )
        if (resId == 0) break
        try {
            val text = context.resources.openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }
                .trim()
            if (text.isNotEmpty()) {
                allPoems.add(text)
            }
        } catch (e: Exception) {
            // Silently skip poems that can't be read
        }
        i++
    }

    // 2. Add all custom poems
    customPoetryManager?.poems?.forEach { poem ->
        if (poem.text.isNotEmpty()) {
            allPoems.add(poem.text)
        }
    }

    // 3. Check if we have any poems at all
    if (allPoems.isEmpty()) return null

    // 4. Pick random poem, avoiding last played
    val selectedPoem = if (allPoems.size > 1 && lastPlayedPoem != null) {
        val availablePoems = allPoems.filter { it != lastPlayedPoem }
        if (availablePoems.isNotEmpty()) {
            availablePoems.random()
        } else {
            allPoems.random()
        }
    } else {
        allPoems.random()
    }

    lastPlayedPoem = selectedPoem
    return selectedPoem
}

/**
 * Starts speaking a random poem from preset files and custom poems
 */
fun startSpeakingRandomPoem(): String? {
    if (!isInitialized) return null

    val poemText = loadRandomPoemFile() ?: return null
    startSpeakingWithPauses(poemText)
    return poemText
}
```

#### F. Add Content Mode Cycling
```kotlin
/**
 * Cycles through content modes: OFF → MEDITATION → POETRY → OFF
 * Includes crossfade loading state when transitioning from MEDITATION → POETRY
 */
suspend fun cycleContentMode() {
    when (contentMode) {
        ContentMode.OFF -> {
            // OFF → MEDITATION (immediate, green)
            startSpeakingRandomMeditation()
            contentMode = ContentMode.MEDITATION
            saveContentMode()
        }
        ContentMode.MEDITATION -> {
            // MEDITATION → POETRY (with loading transition)
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
        ContentMode.MEDITATION -> startSpeakingRandomMeditation()
        ContentMode.POETRY -> startSpeakingRandomPoem()
        ContentMode.OFF -> { /* Do nothing */ }
    }
}
```

#### G. Modify Existing Methods

**In `startSpeakingWithPauses()`**:
```kotlin
// CHANGE:
prefs.edit().putBoolean(PREF_MEDITATION_COMPLETED, false).apply()
// TO:
prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, false).apply()
```

**In `stopSpeaking()`**:
```kotlin
// ADD after isSpeaking = false:
contentMode = ContentMode.OFF
prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, false).apply()
```

**In `didFinishSpeaking()` (the method called when TTS completes)**:
```kotlin
// CHANGE:
prefs.edit().putBoolean(PREF_MEDITATION_COMPLETED, true).apply()
// TO:
prefs.edit().putBoolean(PREF_CONTENT_COMPLETED, true).apply()
```

---

### 5. Create `LoadingDotsIndicator.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/LoadingDotsIndicator.kt` (~60 lines)

```kotlin
package com.jmisabella.zrooms

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Three pulsing dots loading indicator for meditation→poetry transition
 */
@Composable
fun LoadingDotsIndicator(
    color: Color = Color(0xFFFFB74D),  // Orange/amber
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val delays = listOf(0, 150, 300)  // Stagger the dots

    Row(modifier = modifier) {
        delays.forEach { delay ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$delay"
            )

            Canvas(
                modifier = Modifier
                    .size(6.dp)
                    .padding(horizontal = 2.dp)
            ) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2
                )
            }
        }
    }
}
```

---

### 6. Update `ExpandingView.kt`

**File**: `app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt`

#### A. Add Imports (top of file)
```kotlin
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

#### B. Add Poetry Manager (~line 124)
```kotlin
// After:
val meditationManager = remember { CustomMeditationManager(context) }

// ADD:
val poetryManager = remember { CustomPoetryManager(context) }
```

#### C. Update TTS Manager Construction (~line 127)
```kotlin
// CHANGE FROM:
val ttsManager = remember { TextToSpeechManager(context, meditationManager) }

// TO:
val ttsManager = remember {
    TextToSpeechManager(context, meditationManager, poetryManager)
}
```

#### D. Add Coroutine Scope (~line 130)
```kotlin
val scope = rememberCoroutineScope()
```

#### E. Replace State Variable (~line 129)
```kotlin
// REMOVE:
var isMeditationPlaying by remember { mutableStateOf(false) }

// ADD:
var contentMode by remember { mutableStateOf(ttsManager.contentMode) }
var isLoading by remember { mutableStateOf(ttsManager.isLoading) }
```

#### F. Rename Dialog State (~line 130)
```kotlin
// CHANGE:
var showMeditationList by remember { mutableStateOf(false) }

// TO:
var showContentBrowser by remember { mutableStateOf(false) }
```

#### G. Update State Tracking (~line 180)
```kotlin
// REMOVE:
LaunchedEffect(ttsManager.isPlayingMeditation) {
    isMeditationPlaying = ttsManager.isPlayingMeditation
}

// ADD:
LaunchedEffect(ttsManager.contentMode) {
    contentMode = ttsManager.contentMode
}

LaunchedEffect(ttsManager.isLoading) {
    isLoading = ttsManager.isLoading
}
```

#### H. Update Quote Button (~line 502)
```kotlin
// CHANGE onClick:
onClick = { showContentBrowser = true }  // was: showMeditationList = true

// CHANGE contentDescription:
contentDescription = "Meditations & Poems"  // was: "Custom Meditations"
```

#### I. Replace Leaf Button (find the Eco icon button ~line 541)
```kotlin
// REPLACE entire Box containing Eco icon WITH:
Box(
    modifier = Modifier
        .clickable {
            scope.launch {
                ttsManager.cycleContentMode()
            }
        }
        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        .padding(12.dp),
    contentAlignment = Alignment.Center
) {
    // Show loading indicator during crossfade
    if (isLoading) {
        LoadingDotsIndicator(
            color = Color(0xFFFFB74D)  // Orange/amber color
        )
    } else {
        Icon(
            imageVector = when (contentMode) {
                ContentMode.MEDITATION -> Icons.Outlined.Eco
                ContentMode.POETRY -> Icons.Filled.TheaterComedy
                ContentMode.OFF -> Icons.Outlined.Eco
            },
            contentDescription = when (contentMode) {
                ContentMode.MEDITATION -> "Stop Meditation"
                ContentMode.POETRY -> "Stop Poetry"
                ContentMode.OFF -> "Start Content"
            },
            tint = when (contentMode) {
                ContentMode.MEDITATION -> Color(0xFF4CAF50)  // Green
                ContentMode.POETRY -> Color(0xFFAB47BC)       // Purple
                ContentMode.OFF -> Color(0xFF9E9E9E)          // Gray
            },
            modifier = Modifier.size(28.dp)
        )
    }
}
```

#### J. Update MeditationTextDisplay Call (~line 593)
```kotlin
// CHANGE:
isVisible = showMeditationText.value && isMeditationPlaying,

// TO:
isVisible = showMeditationText.value && contentMode != ContentMode.OFF,
```

#### K. Replace Dialog Section (~line 745)
```kotlin
// REMOVE:
if (showMeditationList) {
    CustomMeditationListView(
        manager = meditationManager,
        onDismiss = { showMeditationList = false },
        onPlay = { meditationText ->
            ttsManager.startSpeakingWithPauses(meditationText)
        }
    )
}

// ADD:
if (showContentBrowser) {
    ContentBrowserView(
        meditationManager = meditationManager,
        poetryManager = poetryManager,
        onDismiss = { showContentBrowser = false },
        onPlayMeditation = { text ->
            ttsManager.startSpeakingWithPauses(text)
            ttsManager.contentMode = ContentMode.MEDITATION
        },
        onPlayPoem = { text ->
            ttsManager.startSpeakingWithPauses(text)
            ttsManager.contentMode = ContentMode.POETRY
        }
    )
}
```

---

### 7. Create `ContentBrowserView.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/ContentBrowserView.kt` (~200 lines)

```kotlin
package com.jmisabella.zrooms

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

enum class ContentTab {
    MEDITATIONS,
    POEMS
}

@Composable
fun ContentBrowserView(
    meditationManager: CustomMeditationManager,
    poetryManager: CustomPoetryManager,
    onDismiss: () -> Unit,
    onPlayMeditation: (String) -> Unit,
    onPlayPoem: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(ContentTab.MEDITATIONS) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFF212121)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab buttons
                    Row {
                        TabButton(
                            text = "Meditations",
                            isSelected = selectedTab == ContentTab.MEDITATIONS,
                            onClick = { selectedTab = ContentTab.MEDITATIONS }
                        )
                        Spacer(Modifier.width(8.dp))
                        TabButton(
                            text = "Poems",
                            isSelected = selectedTab == ContentTab.POEMS,
                            onClick = { selectedTab = ContentTab.POEMS }
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color(0xFF424242))

                // Content area - show appropriate list
                when (selectedTab) {
                    ContentTab.MEDITATIONS -> {
                        CustomMeditationListContent(
                            manager = meditationManager,
                            onPlay = { text ->
                                onPlayMeditation(text)
                                onDismiss()
                            }
                        )
                    }
                    ContentTab.POEMS -> {
                        CustomPoemListContent(
                            manager = poetryManager,
                            onPlay = { text ->
                                onPlayPoem(text)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isSelected) {
                Color(0xFF4CAF50)  // Green when selected
            } else {
                Color(0xFF424242)  // Dark gray when not selected
            },
            contentColor = Color.White
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text)
    }
}
```

---

### 8. Modify `CustomMeditationListView.kt`

**File**: `app/src/main/java/com/jmisabella/zrooms/CustomMeditationListView.kt`

**Strategy**: Extract the content portion into a reusable `CustomMeditationListContent()` composable that can be embedded in `ContentBrowserView`. Keep the original `CustomMeditationListView()` for backward compatibility.

**Implementation**: The existing view content (everything inside the Dialog/Surface) should be extracted into `CustomMeditationListContent()`. The original function becomes a wrapper that shows the Dialog with the content inside.

(Detailed implementation will be straightforward - extract the LazyColumn and related UI into a new composable, then call it from both the original dialog view and from ContentBrowserView)

---

### 9. Create `CustomPoemListView.kt`

**New File**: `app/src/main/java/com/jmisabella/zrooms/CustomPoemListView.kt` (~320 lines)

**Implementation**: Copy `CustomMeditationListView.kt` and make these find/replace changes:

| Original | Replace With |
|----------|-------------|
| `CustomMeditation` | `CustomPoem` |
| `CustomMeditationManager` | `CustomPoetryManager` |
| `meditations` | `poems` |
| `meditation` | `poem` |
| "Meditation" | "Poem" |
| "meditation" | "poem" |
| `Color(0xFF4CAF50)` | `Color(0xFFAB47BC)` (green → purple) |
| `Icons.Filled.FormatQuote` | `Icons.Filled.TheaterComedy` (empty state icon) |

**Key Components**:
- `CustomPoemListContent()` - Content-only version for ContentBrowserView
- `CustomPoemListView()` - Dialog wrapper for backward compatibility
- `PoemRow()` - Individual poem list item
- `EmptyPoemState()` - Empty state with theater masks icon

---

### 10. Update `AudioService.kt`

**File**: `app/src/main/java/com/jmisabella/zrooms/AudioService.kt`

**Find and Replace** (approximately lines 374 and 456):

```kotlin
// CHANGE variable name:
val meditationCompleted = prefs.getBoolean(TextToSpeechManager.PREF_MEDITATION_COMPLETED, false)

// TO:
val contentCompleted = prefs.getBoolean(TextToSpeechManager.PREF_CONTENT_COMPLETED, false)

// CHANGE condition:
if (meditationCompleted) {
    scheduleWakeUpGreeting()
}

// TO:
if (contentCompleted) {
    scheduleWakeUpGreeting()
}

// CHANGE flag clearing:
prefs.edit().putBoolean(TextToSpeechManager.PREF_MEDITATION_COMPLETED, false).apply()

// TO:
prefs.edit().putBoolean(TextToSpeechManager.PREF_CONTENT_COMPLETED, false).apply()
```

---

### 11. Create Poem Content Files

#### A. Create `default_custom_poem.txt`

**File**: `app/src/main/res/raw/default_custom_poem.txt`

```
The Peace of Wild Things (0.3s)
by Wendell Berry (2s)

When despair for the world grows in me (0.6s)
and I wake in the night at the least sound (0.6s)
in fear of what my life and my children's lives may be, (1.5s)
I go and lie down where the wood drake (0.6s)
rests in his beauty on the water, and the great heron feeds. (2s)

I come into the peace of wild things (0.6s)
who do not tax their lives with forethought (0.6s)
of grief. (1s)
I come into the presence of still water. (2s)

And I feel above me the day-blind stars (0.6s)
waiting with their light. (1s)
For a time (0.6s)
I rest in the grace of the world, and am free. (4s)
```

#### B. Create Preset Poems

**Files**: `app/src/main/res/raw/preset_poem1.txt` through `preset_poem35.txt`

**Content Suggestions**:
- Classic poetry: Robert Frost, Emily Dickinson, Walt Whitman, William Wordsworth
- Haiku collections: Matsuo Basho, Kobayashi Issa
- Nature poetry: Mary Oliver, Wendell Berry
- Mindfulness-themed poems
- Contemporary accessible poems
- **Important**: All must be public domain or original works

**Format**: Use same pause syntax as meditations: `(2s)`, `(1.5m)`, etc.

---

## Testing Checklist

### Core Functionality Tests

#### 3-State Toggle
- [ ] **Test 1**: Tap gray leaf → Meditation starts, icon becomes green filled leaf
- [ ] **Test 2**: Tap green leaf during meditation → Loading dots appear → Poetry starts after ~500ms, icon becomes purple theater masks
- [ ] **Test 3**: Tap purple masks during poetry → Stops, icon becomes gray leaf
- [ ] **Test 4**: Rapid tapping during transitions → No crashes, state machine handles correctly

#### Random Selection
- [ ] **Test 5**: Meditation mode selects randomly from presets + customs
- [ ] **Test 6**: Poetry mode selects randomly from presets + customs
- [ ] **Test 7**: Create custom meditation → Appears in meditation random pool
- [ ] **Test 8**: Create custom poem → Appears in poetry random pool
- [ ] **Test 9**: No consecutive meditation repeats
- [ ] **Test 10**: No consecutive poetry repeats

#### Persistence
- [ ] **Test 11**: Enable meditation, close app, reopen → Meditation auto-plays
- [ ] **Test 12**: Enable poetry, close app, reopen → Poetry auto-plays
- [ ] **Test 13**: Disable (gray leaf), close app, reopen → Stays off
- [ ] **Test 14**: Switch rooms while meditation playing → Mode persists
- [ ] **Test 15**: Switch rooms while poetry playing → Mode persists

#### Wake Greeting
- [ ] **Test 16**: Meditation completes before alarm → Greeting plays when alarm sounds
- [ ] **Test 17**: Poetry completes before alarm → Greeting plays when alarm sounds
- [ ] **Test 18**: Stop meditation manually before alarm → No greeting
- [ ] **Test 19**: Stop poetry manually before alarm → No greeting

### UI Tests

#### Content Browser
- [ ] **Test 20**: Quote button opens ContentBrowserView
- [ ] **Test 21**: Tab switching works (Meditations ↔ Poems)
- [ ] **Test 22**: Tap "Meditations" tab → Shows meditation list
- [ ] **Test 23**: Tap "Poems" tab → Shows poem list (purple accents)
- [ ] **Test 24**: Close button dismisses entire ContentBrowserView

#### Button Layout
- [ ] **Test 25**: ExpandingView has exactly 4 buttons: Settings, Quote, Clock, Leaf/Masks
- [ ] **Test 26**: All buttons fit properly in portrait and landscape

#### Icons & Colors
- [ ] **Test 27**: Off mode shows gray outlined leaf
- [ ] **Test 28**: Meditation mode shows green leaf
- [ ] **Test 29**: Poetry mode shows purple theater masks
- [ ] **Test 30**: Loading indicator shows 3 orange/amber pulsing dots

#### Closed Captioning
- [ ] **Test 31**: CC toggle works for meditation
- [ ] **Test 32**: CC toggle works for poetry
- [ ] **Test 33**: CC displays current phrase during poetry playback

### Custom Content Management

#### Meditation CRUD (Regression Test)
- [ ] **Test 34**: Create new meditation → Saved successfully
- [ ] **Test 35**: Edit existing meditation → Changes persist
- [ ] **Test 36**: Delete meditation → Removed from list
- [ ] **Test 37**: Duplicate meditation → Creates copy with "(Copy)" suffix
- [ ] **Test 38**: Delete all meditations → Default meditation restores

#### Poem CRUD
- [ ] **Test 39**: Create new poem → Saved successfully
- [ ] **Test 40**: Edit existing poem → Changes persist
- [ ] **Test 41**: Delete poem → Removed from list
- [ ] **Test 42**: Duplicate poem → Creates copy with "(Copy)" suffix
- [ ] **Test 43**: Delete all poems → Default poem restores

### Edge Cases

#### Pause Syntax
- [ ] **Test 44**: Poem with `(2s)` pauses → Pauses work correctly
- [ ] **Test 45**: Poem with `(1.5m)` pauses → Minute conversion works
- [ ] **Test 46**: Poem with no pause markers → Auto-pauses added

#### Voice Settings
- [ ] **Test 47**: Voice preference applies to both meditations and poetry
- [ ] **Test 48**: Change voice → Affects both content types

#### Error Handling
- [ ] **Test 49**: No preset poems in bundle → Graceful fallback (uses customs only)
- [ ] **Test 50**: No custom poems → Default poem available
- [ ] **Test 51**: Empty meditation list → Default meditation available

---

## Implementation Order

### Recommended Sequence (dependencies first)

**Day 1: Data Layer**
1. Create `ContentMode.kt` enum
2. Create `CustomPoem.kt` data class
3. Create `CustomPoetryManager.kt` manager
4. Create `default_custom_poem.txt` resource
5. Test: Verify poetry manager CRUD operations

**Day 2: TTS Integration**
1. Update `TextToSpeechManager.kt`:
   - Add `contentMode` state variable
   - Add `isLoading` state variable
   - Add poetry loading methods
   - Add mode cycling logic
   - Add state persistence methods
2. Test: Verify mode cycling, loading states, persistence

**Day 3: UI Components**
1. Create `LoadingDotsIndicator.kt`
2. Update `ExpandingView.kt`:
   - Add poetry manager
   - Replace leaf button with 3-state button
   - Add loading indicator
3. Test: Verify button cycling, icon changes, loading animation

**Day 4: Content Browser**
1. Modify `CustomMeditationListView.kt` (extract content)
2. Create `CustomPoemListView.kt`
3. Create `ContentBrowserView.kt` with tabs
4. Update `ExpandingView.kt` quote button
5. Test: Verify tab navigation, content display

**Day 5: Wake Greeting + Content**
1. Update `AudioService.kt` flag rename
2. Create 35 `preset_poem1.txt` through `preset_poem35.txt` files
3. Test: Verify greeting works for both content types
4. Test: Verify all poems load and play correctly

**Day 6: Integration Testing**
1. End-to-end testing of all features
2. State persistence testing
3. Edge case testing
4. Performance testing
5. Bug fixes and polish

---

## Critical Files Summary

### New Files to Create (8 Kotlin + 36 text resources)

**Kotlin Files**:
1. `app/src/main/java/com/jmisabella/zrooms/ContentMode.kt` (~15 lines)
2. `app/src/main/java/com/jmisabella/zrooms/CustomPoem.kt` (~14 lines)
3. `app/src/main/java/com/jmisabella/zrooms/CustomPoetryManager.kt` (~120 lines)
4. `app/src/main/java/com/jmisabella/zrooms/ContentBrowserView.kt` (~200 lines)
5. `app/src/main/java/com/jmisabella/zrooms/CustomPoemListView.kt` (~320 lines)
6. `app/src/main/java/com/jmisabella/zrooms/LoadingDotsIndicator.kt` (~60 lines)

**Text Resource Files**:
7. `app/src/main/res/raw/default_custom_poem.txt`
8. `app/src/main/res/raw/preset_poem1.txt` through `preset_poem35.txt` (35 files)

**Total New Code**: ~730 Kotlin lines + 36 text files

### Files to Modify (5 files)

1. **`app/src/main/java/com/jmisabella/zrooms/TextToSpeechManager.kt`**
   - Add poetry support, content mode cycling, loading state
   - ~200 new lines, ~30 modified lines

2. **`app/src/main/java/com/jmisabella/zrooms/ExpandingView.kt`**
   - 3-state button, loading indicator, poetry manager
   - ~80 new lines, ~50 modified lines

3. **`app/src/main/java/com/jmisabella/zrooms/CustomMeditationListView.kt`**
   - Extract content component for reuse
   - ~100 new lines, ~40 modified lines

4. **`app/src/main/java/com/jmisabella/zrooms/AudioService.kt`**
   - Rename wake greeting flag
   - ~5 new lines, ~10 modified lines

5. **`CHANGE_LOG.md`**
   - Document all changes with timestamp

**Total Modified Code**: ~385 new lines, ~130 modified lines

---

## Success Criteria

✅ User can toggle through 3 states: Off → Meditation → Poetry → Off
✅ Mode persists across app sessions and room changes
✅ Separate "Meditations" and "Poems" lists accessible via tabs
✅ Quote button opens ContentBrowserView with tabbed interface
✅ Exactly 4 buttons remain in ExpandingView
✅ Wake greeting plays for both meditation and poetry
✅ Crossfade transition with loading indicator feels natural (~500ms)
✅ All existing meditation functionality preserved
✅ Zero migration issues for existing users

---

## Future Enhancements

**Post-v1 Features**:
- Content collections (themed sets)
- Favorites/starring system
- Playback history
- Custom mode icons/colors per user preference
- Different voice selection per mode
- "Never repeat" shuffle option within session
- Full 35 preset poem library with curated content
- Search/filter in ContentBrowserView
- Import/export custom content

---

**Document Version**: 1.0
**Last Updated**: December 30, 2024
**Status**: Ready for Implementation
