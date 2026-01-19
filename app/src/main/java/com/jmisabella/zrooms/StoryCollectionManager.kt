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

    private val chapterPositions: MutableMap<String, Int> = mutableMapOf()

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
                if (dirName.endsWith(".txt")) {
                    println("⏭️ Skipping file: $dirName")
                    continue
                }

                var collection = StoryCollection(
                    directoryName = dirName,
                    displayName = StoryCollection.formatDisplayName(dirName)
                )

                // Scan stories/ subdirectory
                try {
                    val storyFiles = assetManager.list("tts_content/$dirName/stories") ?: emptyArray()
                    collection = collection.copy(
                        storyFiles = storyFiles
                            .filter { it.endsWith(".txt") }
                            .mapNotNull { filename ->
                                extractSequenceNumber(filename)?.let { seq ->
                                    StoryCollection.StoryFile(filename, seq)
                                }
                            }
                    )
                } catch (e: Exception) { }

                // Scan poems/ subdirectory
                try {
                    val poemFiles = assetManager.list("tts_content/$dirName/poems") ?: emptyArray()
                    collection = collection.copy(
                        poemFiles = poemFiles.filter { it.endsWith(".txt") }
                    )
                } catch (e: Exception) { }

                if (collection.storyFiles.isNotEmpty() || collection.poemFiles.isNotEmpty()) {
                    loadedCollections.add(collection)
                }
            }

            collections.addAll(loadedCollections.sortedBy { it.directoryName })
            println("✅ Loaded ${collections.size} collections: ${collections.map { it.directoryName }.joinToString()}")

            // Migration: Check if saved ID is an old UUID format (contains dashes)
            // If so, reset to default since IDs are now directory names
            if (selectedCollectionId != null && selectedCollectionId!!.contains("-")) {
                println("🔄 Migrating from old UUID-based ID to directory name")
                selectedCollectionId = null
            }

            // Auto-select "signal_decay" for new users or after migration
            if (selectedCollectionId == null && collections.isNotEmpty()) {
                val defaultCollection = collections.find { it.directoryName == "signal_decay" }
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

            // If we have a saved ID but it doesn't match any collection, or if we have no saved ID,
            // fallback to first available collection and save it
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

        val path = "tts_content/${collection.directoryName}/stories/${chapter.filename}"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            null
        }
    }

    fun getRandomPoem(collection: StoryCollection): String? {
        if (collection.poemFiles.isEmpty()) return null

        val randomFile = collection.poemFiles.random()
        val path = "tts_content/${collection.directoryName}/poems/$randomFile"
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            null
        }
    }
}
