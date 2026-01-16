package com.jmisabella.zrooms

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager for custom stories with persistence to SharedPreferences
 */
class CustomStoryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("custom_stories", Context.MODE_PRIVATE)
    private val storageKey = "customStories"
    private val maxStories = 35
    private val gson = Gson()

    val stories = mutableStateListOf<CustomStory>()

    val canAddMore: Boolean
        get() = stories.size < maxStories

    init {
        loadStories()
    }

    fun loadStories() {
        val json = prefs.getString(storageKey, null)
        if (json != null) {
            val type = object : TypeToken<List<CustomStory>>() {}.type
            val loaded: List<CustomStory> = gson.fromJson(json, type)
            stories.clear()
            stories.addAll(loaded)
        }

        // If all stories were deleted, restore default
        if (stories.isEmpty()) {
            loadDefaultStory()
        }
    }

    private fun loadDefaultStory() {
        try {
            val text = context.resources.openRawResource(
                context.resources.getIdentifier(
                    "default_custom_story",
                    "raw",
                    context.packageName
                )
            ).bufferedReader().use { it.readText() }.trim()

            val defaultStory = CustomStory(
                title = "Welcome Story",
                text = text
            )
            stories.add(defaultStory)
            saveStories()
        } catch (e: Exception) {
            // Silently handle failure to load default story
        }
    }

    fun saveStories() {
        val json = gson.toJson(stories)
        prefs.edit().putString(storageKey, json).apply()
    }

    fun addStory(story: CustomStory) {
        if (stories.size >= maxStories) return
        stories.add(story)
        saveStories()
    }

    fun updateStory(story: CustomStory) {
        val index = stories.indexOfFirst { it.id == story.id }
        if (index != -1) {
            stories[index] = story
            saveStories()
        }
    }

    fun deleteStory(story: CustomStory) {
        stories.removeIf { it.id == story.id }
        saveStories()

        // If all stories deleted, restore default
        if (stories.isEmpty()) {
            loadDefaultStory()
        }
    }

    fun duplicateStory(story: CustomStory) {
        if (stories.size >= maxStories) return

        val duplicate = CustomStory(
            title = "${story.title} (Copy)",
            text = story.text
        )

        // Insert right after the original
        val index = stories.indexOfFirst { it.id == story.id }
        if (index != -1) {
            stories.add(index + 1, duplicate)
        } else {
            stories.add(duplicate)
        }

        saveStories()
    }

    fun getRandomStory(): CustomStory? {
        return if (stories.isNotEmpty()) {
            stories.random()
        } else {
            null
        }
    }
}
