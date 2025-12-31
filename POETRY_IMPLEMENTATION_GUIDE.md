# Poetry Feature Implementation Guide

**Project**: z rooms iOS App
**Feature**: Add poetry functionality alongside meditation with 3-state toggle
**Date**: December 2024
**Status**: Completed (iOS), Ready for Android Implementation
**Last Updated**: December 30, 2024

---

## What's New (v1.1 - Dec 30, 2024)

**UX Improvement: Loading Indicator During Meditation → Poetry Transition**

During implementation, we discovered that the brief gap during crossfade transitions (meditation → poetry) was confusing to users - the button would temporarily show a grey leaf, making it appear as if playback had stopped.

**Solution**: Added an animated loading indicator (three pulsing dots) that displays during the meditation→poetry transition. This provides clear visual feedback that the system is working.

**Implementation details**: See sections 6 and Android Translation Guide for complete code examples.

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture Summary](#architecture-summary)
3. [Implementation Phases](#implementation-phases)
4. [Detailed File Changes](#detailed-file-changes)
5. [Testing Guide](#testing-guide)
6. [Android Translation Guide](#android-translation-guide)

---

## Overview

### Feature Description
Transform the meditation app to support both guided meditations and poetry readings. The Leaf button becomes a 3-state toggle:
- **State 1**: Off (gray leaf icon)
- **State 2**: Meditation mode (green leaf icon) - plays random meditation
- **State 3**: Poetry mode (purple theater masks icon) - plays random poem

### Key Requirements
✅ 3-state toggle button cycle (Off → Meditation → Poetry → Off)
✅ Mode persists across app sessions
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

## Architecture Summary

### New Components
```
Models/
  ├─ CustomPoem.swift                    [NEW] Poem data model
  └─ ContentMode.swift (optional)        [NEW] Enum: off/meditation/poetry

Views/
  ├─ ContentBrowserView.swift            [NEW] Tabbed interface (Meditations | Poems)
  ├─ CustomPoemListView.swift            [NEW] Browse/manage poems
  └─ CustomPoemEditorView.swift          [NEW] Create/edit poems

Views/Components/
  └─ CustomPoemManager.swift             [NEW] Poem CRUD & persistence

Poems/                                   [NEW DIRECTORY]
  ├─ default_custom_poem.txt             [NEW] Placeholder default poem
  └─ preset_poem1.txt                    [NEW] Placeholder preset poem
```

### Modified Components
```
Views/Components/
  └─ TextToSpeechManager.swift           [MODIFY] Add poetry support, content mode

Views/
  ├─ ExpandingView.swift                 [MODIFY] 3-state toggle, ContentBrowserView
  ├─ CustomMeditationListView.swift      [MODIFY] Remove navigation elements
  └─ ContentView.swift                   [MODIFY] Wake greeting flag rename
```

### State Management
```
ContentMode Enum:
  - off: Nothing playing
  - meditation: Random meditation playing
  - poetry: Random poem playing

UserDefaults Keys:
  - "customPoems": JSON array of CustomPoem objects
  - "contentMode": Current session mode
  - "lastContentMode": Persisted preference (meditation | poetry)
  - "contentCompletedSuccessfully": Wake greeting flag (replaces "meditationCompletedSuccessfully")
```

---

## Implementation Phases

### Phase 1: Foundation & Data Models
**Goal**: Create core data structures and poem storage

**Tasks**:
1. Create `Models/CustomPoem.swift` - identical structure to CustomMeditation
2. Create ContentMode enum (inline in TextToSpeechManager or separate file)
3. Create `Views/Components/CustomPoemManager.swift` - mirrors CustomMeditationManager
4. Create `Poems/` directory with 2 placeholder files:
   - `default_custom_poem.txt`
   - `preset_poem1.txt`

**Estimated Effort**: 1-2 hours

---

### Phase 2: TextToSpeechManager Updates
**Goal**: Add poetry playback support and content mode cycling

**Tasks**:
1. Add `currentContentMode: ContentMode` property
2. Add `customPoemManager` weak reference
3. Create `getRandomPoem()` method (mirrors `getRandomMeditation()`)
4. Create `cycleContentMode()` method
5. Create `restoreLastSession()` method
6. Update wake greeting flag: `meditationCompletedSuccessfully` → `contentCompletedSuccessfully`

**File**: `Views/Components/TextToSpeechManager.swift`
**Estimated Effort**: 2-3 hours

---

### Phase 3: UI Integration - ExpandingView
**Goal**: Implement 3-state toggle button and integrate ContentBrowserView

**Tasks**:
1. Add `@StateObject poemManager = CustomPoemManager()`
2. Rename `showMeditationList` → `showContentBrowser`
3. Create helper functions: `iconForContentMode()`, `colorForContentMode()`
4. Update Leaf/Masks button icon and color logic
5. Implement 3-way tap gesture handler
6. Create `crossfadeToNextContent()` method
7. Replace meditation list sheet with ContentBrowserView sheet
8. Connect poem manager in onAppear

**File**: `Views/ExpandingView.swift`
**Estimated Effort**: 3-4 hours

---

### Phase 4: Content Management Views
**Goal**: Create unified content browser with tabs

**Tasks**:
1. Create `Views/ContentBrowserView.swift` with segmented control
2. Create `Views/CustomPoemListView.swift` (copy CustomMeditationListView, modify)
3. Create `Views/CustomPoemEditorView.swift` (copy CustomMeditationEditorView, modify)
4. Update `Views/CustomMeditationListView.swift` - remove navigation elements

**Estimated Effort**: 4-5 hours

---

### Phase 5: Wake Greeting Update
**Goal**: Make wake greeting work for both content types

**Tasks**:
1. Update flag name in ContentView from `meditationCompletedSuccessfully` → `contentCompletedSuccessfully`
2. Test greeting plays after meditation completes
3. Test greeting plays after poetry completes

**File**: `Views/ContentView.swift`
**Estimated Effort**: 30 minutes

---

### Phase 6: Testing & Validation
**Goal**: Verify all functionality works correctly

**Tasks**: See [Testing Guide](#testing-guide) below

**Estimated Effort**: 2-3 hours

---

## Detailed File Changes

### 1. Create `Models/CustomPoem.swift`

```swift
import Foundation

struct CustomPoem: Identifiable, Codable {
    let id: UUID
    var title: String
    var text: String
    var dateCreated: Date

    init(id: UUID = UUID(), title: String, text: String, dateCreated: Date = Date()) {
        self.id = id
        self.title = title
        self.text = text
        self.dateCreated = dateCreated
    }
}
```

**Notes**: Identical structure to CustomMeditation ensures consistency

---

### 2. Create ContentMode Enum

**Option A**: Inline in `TextToSpeechManager.swift`
**Option B**: Separate file `Models/ContentMode.swift`

```swift
enum ContentMode: String, Codable {
    case off = "off"
    case meditation = "meditation"
    case poetry = "poetry"

    func next() -> ContentMode {
        switch self {
        case .off: return .meditation
        case .meditation: return .poetry
        case .poetry: return .off
        }
    }
}
```

---

### 3. Create `Views/Components/CustomPoemManager.swift`

**Implementation**: Near-identical copy of `CustomMeditationManager.swift` with these changes:

| Original | Change To |
|----------|-----------|
| `CustomMeditation` | `CustomPoem` |
| `meditations` | `poems` |
| `"customMeditations"` | `"customPoems"` |
| `"default_custom_meditation"` | `"default_custom_poem"` |
| `addMeditation()` | `addPoem()` |
| `updateMeditation()` | `updatePoem()` |
| `deleteMeditation()` | `deletePoem()` |
| `duplicateMeditation()` | `duplicatePoem()` |

**Key Methods**:
- `loadPoems()` - Load from UserDefaults, restore default if empty
- `savePoems()` - JSON encode and persist
- `addPoem()`, `updatePoem()`, `deletePoem()`, `duplicatePoem()` - CRUD operations

---

### 4. Create Placeholder Poem Files

#### `Poems/default_custom_poem.txt`
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

#### `Poems/preset_poem1.txt`
```
Haiku (0.3s)
by Matsuo Basho (2s)

An old silent pond (1s)
A frog jumps into the pond (1s)
Splash! (0.5s) Silence again. (4s)
```

**Notes**:
- Use same pause syntax as meditations: `(2s)`, `(1.5m)`, etc.
- Public domain content only
- More poems can be added later as `preset_poem2.txt` through `preset_poem35.txt`

---

### 5. Update `Views/Components/TextToSpeechManager.swift`

#### Add New Properties (after existing @Published properties)
```swift
@Published private(set) var currentContentMode: ContentMode = .off
weak var customPoemManager: CustomPoemManager?
```

#### Add Random Poem Selection Method (after `getRandomMeditation()`)
```swift
func getRandomPoem() -> String? {
    var allPoems: [(text: String, source: String)] = []

    // Load preset poems (preset_poem1.txt through preset_poem35.txt)
    for i in 1...100 {
        if let url = Bundle.main.url(forResource: "preset_poem\(i)", withExtension: "txt"),
           let text = try? String(contentsOf: url, encoding: .utf8) {
            allPoems.append((text.trimmingCharacters(in: .whitespacesAndNewlines), "preset \(i)"))
        }
    }

    // Add custom poems
    if let manager = customPoemManager {
        for poem in manager.poems {
            allPoems.append((poem.text, "custom: \(poem.title)"))
        }
    }

    return allPoems.randomElement()?.text
}
```

#### Add Content Mode Cycling
```swift
func cycleContentMode() {
    let newMode = currentContentMode.next()
    currentContentMode = newMode
    UserDefaults.standard.set(newMode.rawValue, forKey: "contentMode")

    // Persist preference for session restoration
    if newMode != .off {
        UserDefaults.standard.set(newMode.rawValue, forKey: "lastContentMode")
    }
}
```

#### Add Session Restoration
```swift
func restoreLastSession() {
    guard let savedMode = UserDefaults.standard.string(forKey: "lastContentMode"),
          let mode = ContentMode(rawValue: savedMode) else { return }

    currentContentMode = mode

    // Auto-start content based on mode
    switch mode {
    case .meditation:
        if let text = getRandomMeditation() {
            startSpeakingWithPauses(text)
        }
    case .poetry:
        if let text = getRandomPoem() {
            startSpeakingWithPauses(text)
        }
    case .off:
        break
    }
}
```

#### Update Wake Greeting Flag
**Find and replace** in 3 locations (approximately lines 294, 580, 659):

```swift
// OLD:
UserDefaults.standard.set(true, forKey: "meditationCompletedSuccessfully")
UserDefaults.standard.removeObject(forKey: "meditationCompletedSuccessfully")

// NEW:
UserDefaults.standard.set(true, forKey: "contentCompletedSuccessfully")
UserDefaults.standard.removeObject(forKey: "contentCompletedSuccessfully")
```

---

### 6. Update `Views/ExpandingView.swift`

#### Add Poem Manager (after line 36, near other @StateObject declarations)
```swift
@StateObject private var poemManager = CustomPoemManager()
```

#### Rename State Variable (around line 40)
```swift
// OLD:
@State private var showMeditationList: Bool = false

// NEW:
@State private var showContentBrowser: Bool = false
```

#### Add Helper Functions (before body or at end of struct)
```swift
private func iconForContentMode(_ mode: ContentMode, isPlaying: Bool) -> String {
    switch mode {
    case .off:
        return "leaf"
    case .meditation:
        return isPlaying ? "leaf.fill" : "leaf"
    case .poetry:
        return isPlaying ? "theatermasks.fill" : "theatermasks"
    }
}

private func colorForContentMode(_ mode: ContentMode, isPlaying: Bool) -> Color {
    switch mode {
    case .off:
        return Color(white: 0.7)
    case .meditation:
        return isPlaying ? Color.green : Color(white: 0.7)
    case .poetry:
        return isPlaying ? Color.purple : Color(white: 0.7)
    }
}
```

#### Update Leaf/Masks Button Icon and Color (lines 190-224)
**Replace existing button label** (note: helper functions now take `isCrossfading` parameter):
```swift
.label {
    Image(systemName: iconForContentMode(ttsManager.currentContentMode, isPlaying: ttsManager.isSpeaking, isCrossfading: isCrossfading))
        .font(.title)
        .foregroundColor(colorForContentMode(ttsManager.currentContentMode, isPlaying: ttsManager.isSpeaking, isCrossfading: isCrossfading))
        .symbolEffect(.pulse, options: .repeating, isActive: isCrossfading)
        .padding(10)
        .background(Circle().fill(Color.black.opacity(0.5)))
}
```

**Note**: The `.symbolEffect(.pulse, options: .repeating, isActive: isCrossfading)` line adds an animated pulse effect to the loading indicator (three dots) during the meditation→poetry transition.

#### Update Tap Gesture - 3-Way Toggle
**Replace existing tap gesture handler**:
```swift
.simultaneousGesture(
    TapGesture().onEnded { _ in
        switch ttsManager.meditationState {
        case .idle:
            // Cycle to next mode and start content
            ttsManager.cycleContentMode()

            switch ttsManager.currentContentMode {
            case .meditation:
                guard let text = ttsManager.getRandomMeditation() else { return }
                ttsManager.startSpeakingWithPauses(text)
            case .poetry:
                guard let text = ttsManager.getRandomPoem() else { return }
                ttsManager.startSpeakingWithPauses(text)
            case .off:
                break
            }

        case .playing:
            // Check if cycling to next content or stopping
            let nextMode = ttsManager.currentContentMode.next()

            if nextMode == .off {
                // Stop completely
                Task {
                    await ttsManager.stopSpeaking()
                    ttsManager.currentContentMode = .off
                    UserDefaults.standard.set("off", forKey: "contentMode")
                }
            } else {
                // Crossfade to next content type
                crossfadeToNextContent()
            }

        case .starting, .stopping:
            break
        }
    }
)
```

#### Add Loading State Variable (around line 50)
```swift
@State private var isCrossfading: Bool = false
```

#### Update Helper Functions to Support Loading State
```swift
private func iconForContentMode(_ mode: ContentMode, isPlaying: Bool, isCrossfading: Bool) -> String {
    // Show loading indicator during crossfade
    if isCrossfading {
        return "ellipsis.circle.fill"
    }

    switch mode {
    case .off:
        return "leaf"
    case .meditation:
        return isPlaying ? "leaf.fill" : "leaf"
    case .poetry:
        return isPlaying ? "theatermasks.fill" : "theatermasks"
    }
}

private func colorForContentMode(_ mode: ContentMode, isPlaying: Bool, isCrossfading: Bool) -> Color {
    // Show grey color during crossfade
    if isCrossfading {
        return Color(white: 0.7)
    }

    switch mode {
    case .off:
        return Color(white: 0.7)
    case .meditation:
        return isPlaying ? Color.green : Color(white: 0.7)
    case .poetry:
        return isPlaying ? Color.purple : Color(white: 0.7)
    }
}
```

#### Add Crossfade Method with Loading Indicator
```swift
private func crossfadeToNextContent() {
    let currentMode = ttsManager.currentContentMode
    let nextMode = currentMode.next()

    // Get next content
    let nextText: String?
    switch nextMode {
    case .meditation:
        nextText = ttsManager.getRandomMeditation()
    case .poetry:
        nextText = ttsManager.getRandomPoem()
    case .off:
        nextText = nil
    }

    guard let text = nextText else { return }

    // Show loading indicator only when transitioning from meditation to poetry
    if currentMode == .meditation && nextMode == .poetry {
        isCrossfading = true
    }

    // Crossfade implementation (AVSpeechSynthesizer limitation: no real-time volume)
    Task {
        await ttsManager.stopSpeaking()

        // 1.5 second gap for natural transition
        try? await Task.sleep(nanoseconds: 1_500_000_000)

        // Update mode and start new content
        ttsManager.currentContentMode = nextMode
        UserDefaults.standard.set(nextMode.rawValue, forKey: "contentMode")
        if nextMode != .off {
            UserDefaults.standard.set(nextMode.rawValue, forKey: "lastContentMode")
        }

        // Clear loading state before starting new content
        isCrossfading = false

        ttsManager.startSpeakingWithPauses(text)
    }
}
```

#### Update Quote Button (around line 144)
```swift
// OLD:
Button {
    showMeditationList = true
} label: {
    // ...
}

// NEW:
Button {
    showContentBrowser = true
} label: {
    // ... (no other changes)
}
```

#### Replace Meditation List Sheet with ContentBrowserView (around line 442)
```swift
// OLD:
.sheet(isPresented: $showMeditationList) {
    CustomMeditationListView(
        manager: meditationManager,
        isPresented: $showMeditationList,
        onPlay: { text in
            ttsManager.startSpeakingWithPauses(text)
        }
    )
}

// NEW:
.sheet(isPresented: $showContentBrowser) {
    ContentBrowserView(
        meditationManager: meditationManager,
        poemManager: poemManager,
        isPresented: $showContentBrowser,
        onPlayMeditation: { text in
            ttsManager.startSpeakingWithPauses(text)
        },
        onPlayPoem: { text in
            ttsManager.startSpeakingWithPauses(text)
        }
    )
}
```

#### Connect Managers in onAppear (after line 317)
```swift
ttsManager.customPoemManager = poemManager
ttsManager.restoreLastSession()
```

---

### 7. Create `Views/ContentBrowserView.swift`

**Full implementation**:
```swift
import SwiftUI

struct ContentBrowserView: View {
    @ObservedObject var meditationManager: CustomMeditationManager
    @ObservedObject var poemManager: CustomPoemManager
    @Binding var isPresented: Bool
    let onPlayMeditation: (String) -> Void
    let onPlayPoem: (String) -> Void

    @State private var selectedTab = 0  // 0 = Meditations, 1 = Poems

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Tab Selector
                Picker("Content Type", selection: $selectedTab) {
                    Text("Meditations").tag(0)
                    Text("Poems").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()

                // Content based on selected tab
                if selectedTab == 0 {
                    CustomMeditationListView(
                        manager: meditationManager,
                        isPresented: $isPresented,
                        onPlay: onPlayMeditation
                    )
                    .navigationBarHidden(true)  // Hide inner nav bar
                } else {
                    CustomPoemListView(
                        manager: poemManager,
                        isPresented: $isPresented,
                        onPlay: onPlayPoem
                    )
                    .navigationBarHidden(true)  // Hide inner nav bar
                }
            }
            .navigationTitle("My Content")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        isPresented = false
                    }
                }
            }
        }
    }
}
```

---

### 8. Create `Views/CustomPoemListView.swift`

**Instructions**:
1. Copy entire `Views/CustomMeditationListView.swift` file
2. Save as `Views/CustomPoemListView.swift`
3. Find and replace:
   - `CustomMeditation` → `CustomPoem`
   - `meditation` → `poem`
   - `Meditation` → `Poem`
4. Remove navigation elements:
   - DELETE `.navigationTitle("Custom Meditations")`
   - DELETE `.navigationBarTitleDisplayMode(.inline)`
   - DELETE toolbar Close button (ToolbarItem with placement: .cancellationAction)
5. Update accent colors to purple:
   - CC toggle active color: `Color(hex: 0x64B5F6)` → `Color.purple`
   - Add button color: default blue → `.foregroundColor(.purple)`
6. Update empty state text to reference poems instead of meditations

**Key structural points**:
```swift
struct CustomPoemListView: View {
    @ObservedObject var manager: CustomPoemManager
    @Binding var isPresented: Bool
    let onPlay: (String) -> Void

    @State private var editingPoem: CustomPoem?
    @AppStorage("showMeditationText") private var showMeditationText: Bool = false

    var body: some View {
        VStack(spacing: 0) {
            if manager.poems.isEmpty {
                emptyStateView
            } else {
                poemList
            }
        }
        // NO .navigationTitle() - parent ContentBrowserView handles it
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 16) {
                    // CC Toggle
                    Button {
                        showMeditationText.toggle()
                    } label: {
                        Image(systemName: "captions.bubble.fill")
                            .font(.title3)
                            .foregroundColor(showMeditationText ? Color.purple : Color(hex: 0x757575))
                    }

                    // Add Button
                    if manager.canAddMore {
                        Button {
                            editingPoem = CustomPoem(title: "", text: "")
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                                .foregroundColor(.purple)
                        }
                    }
                }
            }
        }
        .sheet(item: $editingPoem) { poem in
            CustomPoemEditorView(
                manager: manager,
                poem: poem,
                isPresented: Binding(
                    get: { editingPoem != nil },
                    set: { if !$0 { editingPoem = nil } }
                )
            )
        }
    }

    // ... rest mirrors CustomMeditationListView
}
```

---

### 9. Create `Views/CustomPoemEditorView.swift`

**Instructions**:
1. Copy entire `Views/CustomMeditationEditorView.swift` file
2. Save as `Views/CustomPoemEditorView.swift`
3. Find and replace:
   - `CustomMeditation` → `CustomPoem`
   - `CustomMeditationManager` → `CustomPoemManager`
   - `meditation` → `poem`
   - `Meditation` → `Poem`
4. Update placeholders:
   - Title: "Meditation title" → "Poem title"
   - Text: "Meditation text" → "Poem text"
5. Update navigation titles:
   - "New Meditation" → "New Poem"
   - "Edit Meditation" → "Edit Poem"
6. Update accent color to purple where applicable
7. Keep pause syntax help text (works for both)

---

### 10. Update `Views/CustomMeditationListView.swift`

**Remove navigation elements** since parent ContentBrowserView now handles them:

```swift
// DELETE these lines (around line 19-20):
.navigationTitle("Custom Meditations")
.navigationBarTitleDisplayMode(.inline)

// DELETE this toolbar item (around lines 22-26):
.toolbar {
    ToolbarItem(placement: .cancellationAction) {
        Button("Close") {
            isPresented = false
        }
    }
    // ... keep other toolbar items (CC toggle, add button)
}
```

**Result**: Navigation stays inside the view's body, but now relies on parent ContentBrowserView for title and close button.

---

### 11. Update `Views/ContentView.swift`

**Update wake greeting flag** (around lines 493 and 525-535):

```swift
// Line 493 - OLD:
let meditationCompleted = UserDefaults.standard.bool(forKey: "meditationCompletedSuccessfully")

// Line 493 - NEW:
let contentCompleted = UserDefaults.standard.bool(forKey: "contentCompletedSuccessfully")

// Lines 525-535 - OLD:
if meditationCompleted {
    DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
        if self.isAlarmActive {
            self.ttsManager.speakWakeUpGreeting()
        }
    }
    UserDefaults.standard.removeObject(forKey: "meditationCompletedSuccessfully")
}

// Lines 525-535 - NEW:
if contentCompleted {
    DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
        if self.isAlarmActive {
            self.ttsManager.speakWakeUpGreeting()
        }
    }
    UserDefaults.standard.removeObject(forKey: "contentCompletedSuccessfully")
}
```

---

## Testing Guide

### Core Functionality Tests

#### 3-State Toggle
- [ ] **Test 1**: Tap gray leaf → Meditation starts, icon becomes green filled leaf
- [ ] **Test 2**: Tap green leaf during meditation → Poetry starts with 1.5s gap, icon becomes purple theater masks
- [ ] **Test 3**: Tap purple masks during poetry → Stops, icon becomes gray leaf
- [ ] **Test 4**: Tap during transition states (.starting, .stopping) → No action, no crash

#### Random Selection
- [ ] **Test 5**: Meditation mode selects randomly from presets + customs
- [ ] **Test 6**: Poetry mode selects randomly from presets + customs
- [ ] **Test 7**: Create custom meditation → Appears in meditation random pool
- [ ] **Test 8**: Create custom poem → Appears in poetry random pool

#### Persistence
- [ ] **Test 9**: Enable meditation, close app, reopen → New random meditation auto-plays
- [ ] **Test 10**: Enable poetry, close app, reopen → New random poem auto-plays
- [ ] **Test 11**: Disable (gray leaf), close app, reopen → Stays off
- [ ] **Test 12**: Switch rooms while meditation playing → Mode persists, new meditation plays
- [ ] **Test 13**: Switch rooms while poetry playing → Mode persists, new poem plays

#### Wake Greeting
- [ ] **Test 14**: Meditation completes before alarm → Greeting plays when alarm sounds
- [ ] **Test 15**: Poetry completes before alarm → Greeting plays when alarm sounds
- [ ] **Test 16**: Stop meditation manually before alarm → No greeting
- [ ] **Test 17**: Stop poetry manually before alarm → No greeting

### UI Tests

#### Content Browser
- [ ] **Test 18**: Quote button opens ContentBrowserView with "My Content" title
- [ ] **Test 19**: Segmented control shows "Meditations" and "Poems" tabs
- [ ] **Test 20**: Tap "Meditations" tab → Shows meditation list with toolbar items
- [ ] **Test 21**: Tap "Poems" tab → Shows poem list with toolbar items (purple accents)
- [ ] **Test 22**: Close button dismisses entire ContentBrowserView

#### Button Layout
- [ ] **Test 23**: ExpandingView has exactly 4 buttons: Gear, Quote, Clock, Leaf/Masks
- [ ] **Test 24**: All buttons fit properly on iPhone SE (smallest screen)

#### Icons & Colors
- [ ] **Test 25**: Off mode shows gray outlined leaf
- [ ] **Test 26**: Meditation mode shows green filled leaf
- [ ] **Test 27**: Poetry mode shows purple filled theater masks

#### Closed Captioning
- [ ] **Test 28**: CC toggle works for meditation
- [ ] **Test 29**: CC toggle works for poetry
- [ ] **Test 30**: CC displays current phrase during poetry playback

### Custom Content Management

#### Meditation CRUD (Regression Test)
- [ ] **Test 31**: Create new meditation → Saved successfully
- [ ] **Test 32**: Edit existing meditation → Changes persist
- [ ] **Test 33**: Delete meditation → Removed from list
- [ ] **Test 34**: Duplicate meditation → Creates copy with "(Copy)" suffix
- [ ] **Test 35**: Delete all meditations → Default meditation restores

#### Poem CRUD
- [ ] **Test 36**: Create new poem → Saved successfully
- [ ] **Test 37**: Edit existing poem → Changes persist
- [ ] **Test 38**: Delete poem → Removed from list
- [ ] **Test 39**: Duplicate poem → Creates copy with "(Copy)" suffix
- [ ] **Test 40**: Delete all poems → Default poem restores

### Edge Cases

#### Pause Syntax
- [ ] **Test 41**: Poem with `(2s)` pauses → Pauses work correctly
- [ ] **Test 42**: Poem with `(1.5m)` pauses → Minute conversion works
- [ ] **Test 43**: Poem with no pause markers → Auto-pauses added

#### Voice Settings
- [ ] **Test 44**: Voice preference applies to both meditations and poetry
- [ ] **Test 45**: Change voice → Affects both content types

#### Error Handling
- [ ] **Test 46**: No preset poems in bundle → Graceful fallback (uses customs only)
- [ ] **Test 47**: No custom poems → Default poem available
- [ ] **Test 48**: Rapid button tapping → No crashes, state machine handles correctly

---

## Android Translation Guide

### Overview
This implementation plan is framework-agnostic and can be translated to Android using the mappings below.

### Component Mapping

| iOS Component | Android Equivalent |
|--------------|-------------------|
| SwiftUI Views | Jetpack Compose Composables |
| `@StateObject` | ViewModel with StateFlow |
| `@Published` | MutableStateFlow |
| `@Binding` | MutableState parameter |
| UserDefaults | SharedPreferences |
| AVSpeechSynthesizer | android.speech.tts.TextToSpeech |
| Bundle.main.url() | context.assets.open() |
| SF Symbols | Material Icons / Custom Drawables |
| NavigationView | NavHost + NavController |

### Icon Mapping

| iOS SF Symbol | Android Material Icon | Alternative |
|--------------|----------------------|-------------|
| `"leaf"` | `Icons.Outlined.Eco` | Custom drawable |
| `"leaf.fill"` | `Icons.Filled.Eco` | Custom drawable |
| `"theatermasks"` | `Icons.Outlined.TheaterComedy` | Custom drawable |
| `"theatermasks.fill"` | `Icons.Filled.TheaterComedy` | Custom drawable |
| `"ellipsis.circle.fill"` | `Icons.Filled.MoreHoriz` | Three dots (loading) |
| `"text.quote"` | `Icons.Filled.FormatQuote` | - |
| `"captions.bubble.fill"` | `Icons.Filled.ClosedCaption` | - |

### Code Examples

#### ContentMode Enum (Kotlin)
```kotlin
enum class ContentMode {
    OFF, MEDITATION, POETRY;

    fun next(): ContentMode = when(this) {
        OFF -> MEDITATION
        MEDITATION -> POETRY
        POETRY -> OFF
    }
}
```

#### TextToSpeechManager (ViewModel)
```kotlin
class TextToSpeechManager(context: Context) : ViewModel() {
    private val _contentMode = MutableStateFlow(ContentMode.OFF)
    val contentMode: StateFlow<ContentMode> = _contentMode.asStateFlow()

    private val tts = TextToSpeech(context) { status ->
        // Initialize TTS
    }

    fun cycleContentMode() {
        _contentMode.value = _contentMode.value.next()

        // Save to SharedPreferences
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("contentMode", _contentMode.value.name)
            .apply()
    }

    fun getRandomPoem(): String? {
        val poems = mutableListOf<String>()

        // Load from assets
        for (i in 1..100) {
            try {
                val poem = context.assets.open("poems/preset_poem$i.txt")
                    .bufferedReader()
                    .use { it.readText() }
                poems.add(poem)
            } catch (e: IOException) {
                // File doesn't exist, continue
            }
        }

        // Add custom poems from SharedPreferences
        // ...

        return poems.randomOrNull()
    }
}
```

#### ContentBrowserView (Compose)
```kotlin
@Composable
fun ContentBrowserView(
    meditationManager: CustomMeditationManager,
    poemManager: CustomPoemManager,
    onDismiss: () -> Unit,
    onPlayMeditation: (String) -> Unit,
    onPlayPoem: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Content") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Meditations") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Poems") }
                )
            }

            when (selectedTab) {
                0 -> CustomMeditationListView(
                    manager = meditationManager,
                    onPlay = onPlayMeditation
                )
                1 -> CustomPoemListView(
                    manager = poemManager,
                    onPlay = onPlayPoem
                )
            }
        }
    }
}
```

### Storage Strategy (Android)

#### Save Custom Poems
```kotlin
fun savePoems(context: Context, poems: List<CustomPoem>) {
    val json = Gson().toJson(poems)
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("customPoems", json)
        .apply()
}
```

#### Load Custom Poems
```kotlin
fun loadPoems(context: Context): List<CustomPoem> {
    val json = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("customPoems", null) ?: return emptyList()

    return Gson().fromJson(json, Array<CustomPoem>::class.java).toList()
}
```

### TTS Implementation Notes

**Crossfade on Android**: Same limitation as iOS - `TextToSpeech` doesn't support real-time volume control. Use the same stop→pause→start approach:

```kotlin
suspend fun crossfadeToNextContent() {
    val currentMode = contentMode.value
    val nextMode = currentMode.next()

    val text = when (nextMode) {
        ContentMode.MEDITATION -> getRandomMeditation()
        ContentMode.POETRY -> getRandomPoem()
        ContentMode.OFF -> null
    }

    text ?: return

    // Show loading indicator only when transitioning from meditation to poetry
    if (currentMode == ContentMode.MEDITATION && nextMode == ContentMode.POETRY) {
        _isCrossfading.value = true
    }

    tts.stop()
    delay(1500) // 1.5 second gap

    _contentMode.value = nextMode

    // Clear loading state before starting new content
    _isCrossfading.value = false

    speak(text)
}
```

### Loading Indicator (Android)

**Implementation**: Use MutableStateFlow for the crossfading state:

```kotlin
class TextToSpeechManager(context: Context) : ViewModel() {
    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    // ... rest of implementation
}
```

**UI (Compose)**: For the loading icon, use Material Icons or create a custom composable:

```kotlin
@Composable
fun ContentButton(
    contentMode: ContentMode,
    isPlaying: Boolean,
    isCrossfading: Boolean,
    onClick: () -> Unit
) {
    val icon = when {
        isCrossfading -> Icons.Filled.MoreHoriz  // Three dots (horizontal ellipsis)
        contentMode == ContentMode.OFF -> Icons.Outlined.Eco
        contentMode == ContentMode.MEDITATION -> if (isPlaying) Icons.Filled.Eco else Icons.Outlined.Eco
        contentMode == ContentMode.POETRY -> if (isPlaying) Icons.Filled.TheaterComedy else Icons.Outlined.TheaterComedy
        else -> Icons.Outlined.Eco
    }

    val color = when {
        isCrossfading -> Color.Gray
        contentMode == ContentMode.OFF -> Color.Gray
        contentMode == ContentMode.MEDITATION -> if (isPlaying) Color.Green else Color.Gray
        contentMode == ContentMode.POETRY -> if (isPlaying) Color(0xFF9C27B0) else Color.Gray  // Purple
        else -> Color.Gray
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Content Mode",
            tint = color,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isCrossfading) {
                        // Animated pulse effect for loading state
                        Modifier.graphicsLayer {
                            val infiniteTransition = rememberInfiniteTransition()
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            this.alpha = alpha
                        }
                    } else Modifier
                )
        )
    }
}
```

**Note**: Android's Material Icons library includes `Icons.Filled.MoreHoriz` which is a three-dot horizontal ellipsis similar to iOS's `ellipsis.circle.fill`. The pulse animation is achieved using Compose's `animateFloat` with infinite repeating.

---

## Appendix: File Checklist

### New Files to Create (7 files + 1 directory)

**Directory**:
- [ ] `Poems/` (in Xcode project, same level as `Meditations/`)

**Models** (2 files):
- [ ] `Models/CustomPoem.swift`
- [ ] `Models/ContentMode.swift` (optional, can be inline)

**Views** (3 files):
- [ ] `Views/ContentBrowserView.swift`
- [ ] `Views/CustomPoemListView.swift`
- [ ] `Views/CustomPoemEditorView.swift`

**Managers** (1 file):
- [ ] `Views/Components/CustomPoemManager.swift`

**Content** (2 files):
- [ ] `Poems/default_custom_poem.txt`
- [ ] `Poems/preset_poem1.txt`

### Files to Modify (4 files)

- [ ] `Views/Components/TextToSpeechManager.swift`
- [ ] `Views/ExpandingView.swift`
- [ ] `Views/CustomMeditationListView.swift`
- [ ] `Views/ContentView.swift`

---

## Success Criteria

✅ User can toggle through 3 states: Off → Meditation → Poetry → Off
✅ Mode persists across app sessions and room changes
✅ Separate "My Meditations" and "My Poems" lists accessible via tabs
✅ Quote button opens ContentBrowserView with segmented control
✅ Exactly 4 buttons remain in ExpandingView
✅ Wake greeting plays for both meditation and poetry
✅ Crossfade transition feels natural (1-2 seconds)
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
- Full 35 preset poem library
- Android parity implementation

---

**Document Version**: 1.1
**Last Updated**: December 30, 2024
**iOS Status**: ✅ Completed and Tested
**Android Status**: 📋 Ready for Implementation
