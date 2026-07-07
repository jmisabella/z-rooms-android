package com.jmisabella.zrooms

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StoryCollectionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("story_collections", Context.MODE_PRIVATE)
    private val gson = Gson()

    val collections = mutableStateListOf<StoryCollection>()

    var selectedCollectionId by mutableStateOf<String?>(null)
        private set

    var globalPoemFiles: List<String> = emptyList()
        private set

    private val chapterPositions: MutableMap<String, Int> = mutableMapOf()

    companion object {
        private val COLLECTION_ORDER = listOf("sensorium", "aphelion", "calibration")
    }

    init {
        loadChapterPositions()
        loadSelectedCollection()
    }

    fun loadCollections() {
        println("🔄 StoryCollectionManager: Starting loadCollections()")
        collections.clear()
        val loadedCollections = mutableListOf<StoryCollection>()

        try {
            val assetManager = context.assets
            val ttsContentDirs = assetManager.list("tts_content") ?: emptyArray()
            println("📁 Found ${ttsContentDirs.size} items in tts_content: ${ttsContentDirs.joinToString()}")

            for (dirName in ttsContentDirs) {
                if (dirName.endsWith(".txt") || dirName == "poems") {
                    println("⏭️ Skipping: $dirName")
                    continue
                }

                var collection = StoryCollection(
                    directoryName = dirName,
                    displayName = StoryCollection.formatDisplayName(dirName)
                )

                // Scan chapters/ subdirectory
                try {
                    val chapterFiles = assetManager.list("tts_content/$dirName/chapters") ?: emptyArray()
                    collection = collection.copy(
                        storyFiles = chapterFiles
                            .filter { it.endsWith(".txt") }
                            .mapNotNull { filename ->
                                extractSequenceNumber(filename)?.let { seq ->
                                    StoryCollection.StoryFile(filename, seq)
                                }
                            }
                    )
                } catch (e: Exception) { }

                if (collection.storyFiles.isNotEmpty()) {
                    loadedCollections.add(collection)
                }
            }

            // Sort by defined order, unknown names fall to end alphabetically
            val sorted = loadedCollections.sortedWith(compareBy(
                { idx -> COLLECTION_ORDER.indexOf(idx.directoryName).let { if (it == -1) Int.MAX_VALUE else it } },
                { it.directoryName }
            ))
            collections.addAll(sorted)
            println("✅ Loaded ${collections.size} collections: ${collections.map { it.directoryName }.joinToString()}")

            // Load global poem pool
            globalPoemFiles = assetManager.list("tts_content/poems")
                ?.filter { it.endsWith(".txt") }
                ?: emptyList()
            println("🎵 Loaded ${globalPoemFiles.size} global poems")

            // Migration: Check if saved ID is an old UUID format (contains dashes)
            if (selectedCollectionId != null && selectedCollectionId!!.contains("-")) {
                println("🔄 Migrating from old UUID-based ID to directory name")
                selectedCollectionId = null
            }

            // Auto-select "sensorium" for new users or after migration
            if (selectedCollectionId == null && collections.isNotEmpty()) {
                val defaultCollection = collections.find { it.directoryName == "sensorium" }
                    ?: collections.first()
                selectedCollectionId = defaultCollection.id
                saveSelectedCollection()
                println("🎯 Auto-selected collection: ${defaultCollection.displayName} (id: ${defaultCollection.id})")
            } else if (selectedCollectionId != null) {
                println("📌 Previously selected collection id: $selectedCollectionId")
            }

        } catch (e: Exception) {
            println("❌ Error loading collections: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun extractSequenceNumber(filename: String): Int? {
        val regex = "^(\\d+)_".toRegex()
        return regex.find(filename)?.groupValues?.get(1)?.toIntOrNull()
    }

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
        return chapterPositions[directoryName] ?: 0  // 0-based
    }

    fun setChapterIndex(index: Int, directoryName: String) {
        chapterPositions[directoryName] = index
        saveChapterPositions()
    }

    private fun loadSelectedCollection() {
        selectedCollectionId = prefs.getString("selected_collection_id", null)
    }

    private fun saveSelectedCollection() {
        prefs.edit()
            .putString("selected_collection_id", selectedCollectionId)
            .apply()
    }

    fun setSelectedCollection(collectionId: String) {
        selectedCollectionId = collectionId
        saveSelectedCollection()
    }

    val selectedCollection: StoryCollection?
        get() {
            val id = selectedCollectionId
            val found = if (id != null) {
                collections.find { it.id == id }
            } else {
                null
            }

            return found ?: collections.firstOrNull()?.also {
                selectedCollectionId = it.id
                saveSelectedCollection()
            }
        }

    fun getStoryText(collection: StoryCollection, chapterIndex: Int): String? {
        val chapter = collection.sortedChapters.getOrNull(chapterIndex)
            ?: collection.sortedChapters.firstOrNull()?.also {
                setChapterIndex(0, collection.directoryName)
            }
            ?: return null

        val path = "tts_content/${collection.directoryName}/chapters/${chapter.filename}"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            null
        }
    }

    fun getRandomPoem(): String? {
        if (globalPoemFiles.isEmpty()) return null

        val randomFile = globalPoemFiles.random()
        val path = "tts_content/poems/$randomFile"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            null
        }
    }
}
