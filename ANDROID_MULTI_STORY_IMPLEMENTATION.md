# Multi-Story Architecture Implementation - Android Z Rooms

## Overview
Convert Android Z Rooms from single-story structure to scalable multi-story architecture where each story is a self-contained collection with chapters and thematically related poems.

## Current State (Android)
- **Stories**: 8 preset files (`preset_story1.txt` through `preset_story8.txt`) in `res/raw/` directory
- **Poems**: 8 preset files (`preset_poem1.txt` through `preset_poem8.txt`) in `res/raw/` directory
- **File Access**: Resources loaded via `R.raw.preset_story1`, `R.raw.preset_poem1`, etc.
- **Chapter Tracking**: Single SharedPreferences value for `currentChapterIndex` (Int)
- **Leaf Button**: Cycles through story chapters sequentially
- **Theater Button**: Plays random poem from all preset poems
- **Custom Stories/Poems**: Separate SharedPreferences-based system, unaffected by this change

## Design Decisions (Confirmed from iOS Implementation)

### Target Directory Structure

Since Android doesn't support nested directories in `res/raw/`, we'll use **asset files** with directory structure:

```
assets/
├── tts_content/
│   ├── default_custom_story.txt
│   ├── default_custom_poem.txt
│   ├── signal_decay/
│   │   ├── stories/
│   │   │   ├── 01_prelude.txt
│   │   │   ├── 02_chapter1.txt
│   │   │   ├── 03_chapter2.txt
│   │   │   └── ... (numbered sequentially)
│   │   └── poems/
│   │       └── *.txt (any filenames - selected randomly)
│   └── [future_story_name]/
│       ├── stories/
│       └── poems/
```

**Key Android Considerations**:
- Use `AssetManager` instead of `Bundle.main.url()`
- Directory names should be lowercase (Android convention): `signal_decay` not `Signal_Decay`
- File access via `assets.open("tts_content/signal_decay/stories/01_prelude.txt")`
- No R.raw resource IDs needed - all accessed via string paths

### Key Features to Implement

1. **Story Title Display**: Every time Leaf (Story) or Theater (Poetry) button is toggled on, story title appears at top of view for 4-5 seconds with smooth fade animation
   - Title includes a chevron down indicator (∨) to show it's tappable
   - Appears when activating BOTH Story mode AND Poetry mode from idle
   - Appears when cycling between Story ↔ Poetry while playing
2. **Story Selection**: Title is tappable during display - opens dialog/bottom sheet showing all available stories with metadata (chapter count, poem count)
3. **Story Persistence**: Selected story remembered via SharedPreferences between app sessions
   - New users automatically get "signal_decay" as their first story (if it exists)
   - Falls back to first available story alphabetically if signal_decay doesn't exist
   - Existing users with saved preferences are unaffected
4. **Per-Story Chapter Memory**: Each story remembers its chapter position separately (Signal Decay at chapter 4, Other Story at chapter 2, etc.)
5. **Scoped Poems**: Theater button plays random poem ONLY from currently selected story's poems/ directory (not from all stories)
6. **Custom Stories Unchanged**: SharedPreferences-based custom stories/poems remain completely separate from assets/tts_content/ system

### File Naming Conventions
- **Directory names**: Use lowercase with underscores (e.g., `signal_decay`, `my_new_story`)
- **Display names**: Replace underscores with spaces and title-case (e.g., "Signal Decay", "My New Story")
- **Story files**: MUST have numeric prefix for chapter ordering (e.g., `01_`, `02_`, etc.)
- **Poem files**: Can have any name - they're selected randomly

### Migration Approach
- **Manual file reorganization** - No auto-migration implemented
- Copy files from `res/raw/` to new `assets/tts_content/` structure
- Old `res/raw/preset_story*.txt` and `preset_poem*.txt` can remain for reference but won't be used

## Android Implementation Plan

### Phase 1: Create Data Models

#### 1.1 StoryCollection Data Class
**New File**: `app/src/main/java/com/yourpackage/models/StoryCollection.kt`

```kotlin
package com.yourpackage.models

import java.util.UUID

data class StoryCollection(
    val id: String = UUID.randomUUID().toString(),
    val directoryName: String,        // e.g., "signal_decay"
    val displayName: String,           // e.g., "Signal Decay"
    val storyFiles: List<StoryFile> = emptyList(),
    val poemFiles: List<String> = emptyList()
) {
    data class StoryFile(
        val filename: String,
        val sequenceNumber: Int
    )

    companion object {
        fun formatDisplayName(dirName: String): String {
            return dirName.replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    val sortedChapters: List<StoryFile>
        get() = storyFiles.sortedBy { it.sequenceNumber }
}
```

**Purpose**:
- Represents a discovered story collection from assets
- Kotlin data class with default values and companion object
- Provides sorted chapter access for sequential playback

#### 1.2 StoryCollectionManager
**New File**: `app/src/main/java/com/yourpackage/managers/StoryCollectionManager.kt`

```kotlin
package com.yourpackage.managers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourpackage.models.StoryCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class StoryCollectionManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("story_collections", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _collections = MutableStateFlow<List<StoryCollection>>(emptyList())
    val collections: StateFlow<List<StoryCollection>> = _collections.asStateFlow()

    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId.asStateFlow()

    // Per-story chapter tracking: Map<directoryName, chapterIndex>
    private val chapterPositions: MutableMap<String, Int> = mutableMapOf()

    init {
        loadChapterPositions()
        loadSelectedCollection()
    }

    fun loadCollections() {
        val collections = mutableListOf<StoryCollection>()

        try {
            val assetManager = context.assets

            // List directories in tts_content/
            val ttsContentDirs = assetManager.list("tts_content") ?: emptyArray()

            println("📁 StoryCollectionManager: Found ${ttsContentDirs.size} items in tts_content")

            for (dirName in ttsContentDirs) {
                // Skip files (default_custom_story.txt, default_custom_poem.txt)
                if (dirName.endsWith(".txt")) continue

                var collection = StoryCollection(
                    directoryName = dirName,
                    displayName = StoryCollection.formatDisplayName(dirName)
                )

                // Scan stories/ subdirectory
                val storiesPath = "tts_content/$dirName/stories"
                try {
                    val storyFiles = assetManager.list(storiesPath) ?: emptyArray()
                    collection = collection.copy(
                        storyFiles = storyFiles
                            .filter { it.endsWith(".txt") }
                            .mapNotNull { filename ->
                                extractSequenceNumber(filename)?.let { seq ->
                                    StoryCollection.StoryFile(filename, seq)
                                }
                            }
                    )
                } catch (e: IOException) {
                    println("⚠️ No stories directory for $dirName")
                }

                // Scan poems/ subdirectory
                val poemsPath = "tts_content/$dirName/poems"
                try {
                    val poemFiles = assetManager.list(poemsPath) ?: emptyArray()
                    collection = collection.copy(
                        poemFiles = poemFiles.filter { it.endsWith(".txt") }
                    )
                } catch (e: IOException) {
                    println("⚠️ No poems directory for $dirName")
                }

                // Only add if has content
                if (collection.storyFiles.isNotEmpty() || collection.poemFiles.isNotEmpty()) {
                    collections.add(collection)
                    println("   - ${collection.displayName}: ${collection.storyFiles.size} chapters, ${collection.poemFiles.size} poems")
                }
            }

            // Sort alphabetically by directory name
            _collections.value = collections.sortedBy { it.directoryName }

            println("📚 StoryCollectionManager: Loaded ${collections.size} story collections")

            // Auto-select collection if none selected
            // Prefer signal_decay for new users, otherwise use first available
            if (_selectedCollectionId.value == null && collections.isNotEmpty()) {
                val defaultCollection = collections.find { it.directoryName == "signal_decay" }
                    ?: collections.first()
                _selectedCollectionId.value = defaultCollection.id
                saveSelectedCollection()
                println("✨ StoryCollectionManager: Auto-selected '${defaultCollection.displayName}'")
            }

        } catch (e: IOException) {
            println("❌ StoryCollectionManager: Error loading collections - ${e.message}")
        }
    }

    private fun extractSequenceNumber(filename: String): Int? {
        // Extract leading digits before underscore: "01_prelude.txt" -> 1
        val regex = "^(\\d+)_".toRegex()
        return regex.find(filename)?.groupValues?.get(1)?.toIntOrNull()
    }

    // Chapter Position Tracking

    private fun loadChapterPositions() {
        val json = prefs.getString("chapter_positions", "{}")
        val type = object : TypeToken<Map<String, Int>>() {}.type
        chapterPositions.putAll(gson.fromJson(json, type) ?: emptyMap())
    }

    private fun saveChapterPositions() {
        val json = gson.toJson(chapterPositions)
        prefs.edit().putString("chapter_positions", json).apply()
    }

    fun getChapterIndex(directoryName: String): Int {
        return chapterPositions[directoryName] ?: 1  // Default to chapter 1
    }

    fun setChapterIndex(index: Int, directoryName: String) {
        chapterPositions[directoryName] = index
        saveChapterPositions()
    }

    // Selected Collection Management

    private fun loadSelectedCollection() {
        _selectedCollectionId.value = prefs.getString("selected_collection_id", null)
    }

    private fun saveSelectedCollection() {
        prefs.edit()
            .putString("selected_collection_id", _selectedCollectionId.value)
            .apply()
    }

    fun setSelectedCollection(collectionId: String) {
        _selectedCollectionId.value = collectionId
        saveSelectedCollection()
    }

    val selectedCollection: StoryCollection?
        get() {
            val id = _selectedCollectionId.value
            return if (id != null) {
                _collections.value.find { it.id == id }
            } else {
                // Fallback to first available
                _collections.value.firstOrNull()?.also {
                    _selectedCollectionId.value = it.id
                    saveSelectedCollection()
                }
            }
        }

    // Content Loading

    fun getStoryText(collection: StoryCollection, chapterIndex: Int): String? {
        val chapter = collection.sortedChapters.find { it.sequenceNumber == chapterIndex }
            ?: collection.sortedChapters.firstOrNull()?.also {
                // Fall back to first chapter if index out of bounds
                setChapterIndex(it.sequenceNumber, collection.directoryName)
            }
            ?: return null

        val path = "tts_content/${collection.directoryName}/stories/${chapter.filename}"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: IOException) {
            println("❌ Error reading story: ${e.message}")
            null
        }
    }

    fun getRandomPoem(collection: StoryCollection): String? {
        if (collection.poemFiles.isEmpty()) return null

        val randomFile = collection.poemFiles.random()
        val path = "tts_content/${collection.directoryName}/poems/$randomFile"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: IOException) {
            println("❌ Error reading poem: ${e.message}")
            null
        }
    }
}
```

**Key Responsibilities**:
- Scan `assets/tts_content/` directory and discover story subdirectories
- Manage per-story chapter positions via SharedPreferences with Gson JSON encoding
- Provide content loading methods using AssetManager
- Use Kotlin StateFlow for reactive UI updates
- Handle edge cases (missing directories, empty collections, malformed filenames)

### Phase 2: Update TTS/Audio Manager

**File**: Your existing TextToSpeech/Audio manager class

#### 2.1 Add Manager Reference
```kotlin
var storyCollectionManager: StoryCollectionManager? = null
```

#### 2.2 Remove Old Chapter Tracking
```kotlin
// DELETE: SharedPreferences "currentChapterIndex" logic
```

#### 2.3 Update Story Loading
```kotlin
fun getSequentialStory(): String? {
    val manager = storyCollectionManager ?: return null
    val collection = manager.selectedCollection ?: return null

    val chapterIndex = manager.getChapterIndex(collection.directoryName)
    return manager.getStoryText(collection, chapterIndex)
}
```

#### 2.4 Update Chapter Navigation
```kotlin
fun skipToNextChapter(startIfPlaying: Boolean = true) {
    val manager = storyCollectionManager ?: return
    val collection = manager.selectedCollection ?: return

    val currentIndex = manager.getChapterIndex(collection.directoryName)
    val sortedChapters = collection.sortedChapters

    // Find next chapter or wrap to first
    val currentIdx = sortedChapters.indexOfFirst { it.sequenceNumber == currentIndex }
    val nextChapter = if (currentIdx >= 0 && currentIdx + 1 < sortedChapters.size) {
        sortedChapters[currentIdx + 1]
    } else {
        sortedChapters.firstOrNull()
    }

    nextChapter?.let {
        manager.setChapterIndex(it.sequenceNumber, collection.directoryName)

        // Restart if currently playing
        if (startIfPlaying && isPlayingStory) {
            getSequentialStory()?.let { text ->
                stopSpeaking()
                startSpeakingWithPauses(text)
            }
        }
    }
}

fun skipToPreviousChapter(startIfPlaying: Boolean = true) {
    // Similar logic, but navigate backwards
}
```

#### 2.5 Update Poem Loading
```kotlin
fun getRandomPoem(): String? {
    val manager = storyCollectionManager ?: return null
    val collection = manager.selectedCollection ?: return null

    return manager.getRandomPoem(collection)
}
```

**Key Change**: Poems now scoped to current story's `poems/` directory instead of all preset poems

### Phase 3: Add UI Components

#### 3.1 Story Title Overlay

Add to your main activity/fragment layout (XML or Compose):

**Compose Example**:
```kotlin
var showStoryTitle by remember { mutableStateOf(false) }
var storyTitleAlpha by remember { mutableStateOf(0f) }
var showStorySelector by remember { mutableStateOf(false) }

// Story title overlay - appears for 5 seconds when toggling Leaf/Theater
AnimatedVisibility(
    visible = showStoryTitle,
    enter = fadeIn(animationSpec = tween(500)),
    exit = fadeOut(animationSpec = tween(1000))
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        selectedCollection?.let { collection ->
            Button(
                onClick = { showStorySelector = true },
                modifier = Modifier
                    .alpha(storyTitleAlpha),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = collection.displayName,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select story",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Show title briefly when button toggled
fun showTitleBriefly() {
    showStoryTitle = true

    // Fade in
    val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 500
        addUpdateListener { storyTitleAlpha = it.animatedValue as Float }
        start()
    }

    // Fade out after 4.5 seconds
    Handler(Looper.getMainLooper()).postDelayed({
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 1000
            addUpdateListener { storyTitleAlpha = it.animatedValue as Float }
            doOnEnd { showStoryTitle = false }
            start()
        }
    }, 4500)
}
```

**Traditional XML View Example**:
```xml
<!-- In your layout XML -->
<LinearLayout
    android:id="@+id/story_title_overlay"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingHorizontal="20dp"
    android:paddingVertical="12dp"
    android:background="@drawable/rounded_black_bg"
    android:gravity="center_vertical">

    <TextView
        android:id="@+id/story_title_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="20sp"
        android:textColor="@android:color/white" />

    <ImageView
        android:id="@+id/chevron_down"
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:layout_marginStart="8dp"
        android:src="@drawable/ic_keyboard_arrow_down"
        android:alpha="0.8"
        android:tint="@android:color/white" />
</LinearLayout>
```

```kotlin
// In your activity/fragment
private var titleOverlay: LinearLayout? = null
private var titleText: TextView? = null

private fun setupTitleOverlay() {
    titleOverlay = findViewById<LinearLayout>(R.id.story_title_overlay).apply {
        setOnClickListener { showStorySelector() }
    }
    titleText = findViewById(R.id.story_title_text)
}

private fun showTitleBriefly() {
    val collection = storyCollectionManager?.selectedCollection ?: return

    titleOverlay?.apply {
        titleText?.text = collection.displayName
        alpha = 0f
        visibility = View.VISIBLE

        // Fade in
        animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        // Fade out after 4.5 seconds
        postDelayed({
            animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction { visibility = View.GONE }
                .start()
        }, 4500)
    }
}
```

#### 3.2 Story Selection Dialog/Bottom Sheet

**Compose BottomSheet Example**:
```kotlin
if (showStorySelector) {
    ModalBottomSheet(
        onDismissRequest = { showStorySelector = false }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            items(collections) { collection ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            storyCollectionManager?.setSelectedCollection(collection.id)
                            showStorySelector = false
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = collection.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Book, null, tint = Color.Gray)
                                Text(
                                    text = "${collection.storyFiles.size} chapters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            if (collection.poemFiles.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TheaterComedy, null, tint = Color.Gray)
                                    Text(
                                        text = "${collection.poemFiles.size} poems",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    if (selectedCollectionId == collection.id) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
```

**Traditional Dialog/RecyclerView Example**:
```kotlin
private fun showStorySelector() {
    val collections = storyCollectionManager?.collections?.value ?: return

    val adapter = StoryCollectionAdapter(collections) { selectedCollection ->
        storyCollectionManager?.setSelectedCollection(selectedCollection.id)
        dialog.dismiss()
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Select Story")
        .setAdapter(adapter, null)
        .setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
        .create()

    dialog.show()
}

// StoryCollectionAdapter.kt
class StoryCollectionAdapter(
    private val collections: List<StoryCollection>,
    private val onSelect: (StoryCollection) -> Unit
) : RecyclerView.Adapter<StoryCollectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.story_title)
        val chaptersText: TextView = view.findViewById(R.id.chapters_count)
        val poemsText: TextView = view.findViewById(R.id.poems_count)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_story_collection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val collection = collections[position]
        holder.titleText.text = collection.displayName
        holder.chaptersText.text = "${collection.storyFiles.size} chapters"
        holder.poemsText.text = "${collection.poemFiles.size} poems"
        holder.poemsText.visibility = if (collection.poemFiles.isNotEmpty()) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onSelect(collection)
        }
    }

    override fun getItemCount() = collections.size
}
```

### Phase 4: Integration Steps

#### 4.1 Initialize Manager in Activity/Fragment
```kotlin
private lateinit var storyCollectionManager: StoryCollectionManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize manager
    storyCollectionManager = StoryCollectionManager(this)

    // Inject into TTS manager
    ttsManager.storyCollectionManager = storyCollectionManager

    // Load collections
    lifecycleScope.launch {
        storyCollectionManager.loadCollections()

        // Observe collections and selected collection
        storyCollectionManager.collections.collect { collections ->
            // Update UI if needed
        }
    }
}
```

#### 4.2 Trigger Title Display on Button Toggle
```kotlin
// When user toggles Leaf (Story) button from idle
private fun onLeafButtonToggled() {
    if (currentState == State.IDLE) {
        showTitleBriefly()  // Show title when activating Story mode
        // ... rest of button logic
    } else if (currentState == State.PLAYING) {
        // Cycling between modes - show title when switching
        showTitleBriefly()
        // ... crossfade logic
    }
}

// When user toggles Theater (Poetry) button from idle
private fun onTheaterButtonToggled() {
    if (currentState == State.IDLE) {
        showTitleBriefly()  // Show title when activating Poetry mode
        // ... rest of button logic
    } else if (currentState == State.PLAYING) {
        // Cycling between modes - show title when switching
        showTitleBriefly()
        // ... crossfade logic
    }
}
```

### Phase 5: File Migration

#### 5.1 Create Assets Directory Structure
1. Create `app/src/main/assets/tts_content/` directory
2. Create `app/src/main/assets/tts_content/signal_decay/stories/` directory
3. Create `app/src/main/assets/tts_content/signal_decay/poems/` directory

#### 5.2 Copy and Rename Files
```bash
# From res/raw/ to assets/tts_content/signal_decay/

# Stories
preset_story1.txt → assets/tts_content/signal_decay/stories/01_prelude.txt
preset_story2.txt → assets/tts_content/signal_decay/stories/02_chapter1.txt
preset_story3.txt → assets/tts_content/signal_decay/stories/03_chapter2.txt
preset_story4.txt → assets/tts_content/signal_decay/stories/04_chapter3.txt
preset_story5.txt → assets/tts_content/signal_decay/stories/05_chapter4.txt
preset_story6.txt → assets/tts_content/signal_decay/stories/06_chapter5.txt
preset_story7.txt → assets/tts_content/signal_decay/stories/07_chapter6.txt
preset_story8.txt → assets/tts_content/signal_decay/stories/08_chapter7.txt

# Poems (can keep same names or rename)
preset_poem1.txt → assets/tts_content/signal_decay/poems/preset_poem1.txt
preset_poem2.txt → assets/tts_content/signal_decay/poems/preset_poem2.txt
... (all 8 poems)

# Defaults
default_custom_story.txt → assets/tts_content/default_custom_story.txt
default_custom_poem.txt → assets/tts_content/default_custom_poem.txt
```

#### 5.3 Keep Old Files (Optional)
- Old `res/raw/preset_*.txt` files can remain for backward compatibility during transition
- Once verified working, they can be deleted

## Error Handling & Edge Cases

### 1. assets/tts_content/ Missing
- `loadCollections()` returns empty list
- Leaf/Theater buttons show no content when tapped
- Custom stories continue working independently

### 2. Selected Story Deleted
- Fallback to first available collection
- If no collections exist, disable Leaf/Theater buttons

### 3. Empty stories/ or poems/ Subdirectories
- Collections with empty `storyFiles` still added if they have poems
- Leaf button shows nothing if no story files
- Theater button shows nothing if no poem files

### 4. Malformed Filenames (No Sequence Prefix)
- `extractSequenceNumber()` returns null
- File excluded from collection
- Won't break app, just won't be playable

### 5. Chapter Index Out of Bounds
- If stored chapter doesn't exist in new file list, reset to first chapter
- Graceful fallback prevents crashes

## Dependencies Required

### Gradle Dependencies
```kotlin
// In app/build.gradle.kts

dependencies {
    // Gson for JSON serialization (chapter positions)
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin Coroutines (if not already included)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // If using Compose for UI
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // If using Material Design Bottom Sheet
    implementation("com.google.android.material:material:1.11.0")
}
```

## Testing Checklist

- [ ] Launch app with new assets/tts_content/ structure
- [ ] Toggle Leaf button - verify title appears and fades over 5 seconds
- [ ] Tap title during display - verify story selector opens
- [ ] Story selector shows all available stories with metadata
- [ ] Select different story - verify chapters load from correct subdirectory
- [ ] Theater button plays poem from current story's poems/ directory
- [ ] Skip forward/backward through chapters
- [ ] Wrap-around at end/beginning of story
- [ ] Switch to different story, skip chapters, switch back - verify chapter position restored
- [ ] Restart app - verify selected story and chapter positions persist
- [ ] Add new story directory to assets/ - verify it appears in selector
- [ ] Custom stories/poems continue working unchanged

## Future Story Addition Process

1. Create new subdirectory in `assets/tts_content/` (e.g., `my_new_story/`)
2. Create `stories/` and `poems/` subdirectories
3. Add numbered story files: `01_chapter1.txt`, `02_chapter2.txt`, etc.
4. Add poem text files (any names)
5. Rebuild app
6. App automatically discovers and makes available - no code changes needed

## Android-Specific Considerations

### AssetManager vs Resources
- **Assets** support directory structure (chosen for this implementation)
- **Resources** (res/raw/) do not support subdirectories
- AssetManager allows runtime discovery of directory contents
- All file access is string-based, no R.raw resource IDs

### StateFlow vs LiveData
- Example uses Kotlin StateFlow for modern reactive UI
- Can substitute with LiveData if preferred:
  ```kotlin
  private val _collections = MutableLiveData<List<StoryCollection>>()
  val collections: LiveData<List<StoryCollection>> = _collections
  ```

### Compose vs XML Views
- Implementation examples provided for both
- Choose based on your existing app architecture
- Compose offers cleaner state management for this feature

### Gson vs kotlinx.serialization
- Example uses Gson for SharedPreferences JSON encoding
- Can substitute with kotlinx.serialization if preferred:
  ```kotlin
  @Serializable
  data class StoryCollection(...)

  // In manager
  val json = Json.encodeToString(chapterPositions)
  ```

## Summary of Key Differences from iOS

| Aspect | iOS | Android |
|--------|-----|---------|
| **File Location** | `Bundle.main` resources | `AssetManager` assets |
| **Directory Structure** | `TTSContent/Signal_Decay/` | `assets/tts_content/signal_decay/` |
| **Naming Convention** | Mixed case (Signal_Decay) | Lowercase (signal_decay) |
| **File Access** | `Bundle.main.url(forResource:)` | `assets.open("path/to/file")` |
| **Persistence** | `@AppStorage` with UserDefaults | SharedPreferences with Gson |
| **Reactive State** | `@Published` / `@StateObject` | StateFlow / LiveData |
| **UI Framework** | SwiftUI | Compose / XML Views |
| **Language** | Swift | Kotlin |

## Implementation Complete

Once all phases are complete, the Android Z Rooms app will have feature parity with the iOS version:
- ✅ Multi-story architecture with directory-based organization
- ✅ Story title display with fade animation
- ✅ Story selection UI
- ✅ Per-story chapter memory
- ✅ Scoped poem playback
- ✅ Automatic story discovery
- ✅ Custom stories unchanged
