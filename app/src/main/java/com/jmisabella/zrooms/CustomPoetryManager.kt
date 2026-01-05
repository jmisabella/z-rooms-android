package com.jmisabella.zrooms

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager for custom poems with persistence to SharedPreferences
 */
class CustomPoetryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("custom_poems", Context.MODE_PRIVATE)
    private val storageKey = "customPoems"
    private val maxPoems = 35
    private val gson = Gson()

    val poems = mutableStateListOf<CustomPoem>()

    val canAddMore: Boolean
        get() = poems.size < maxPoems

    init {
        loadPoems()
    }

    fun loadPoems() {
        val json = prefs.getString(storageKey, null)
        if (json != null) {
            val type = object : TypeToken<List<CustomPoem>>() {}.type
            val loaded: List<CustomPoem> = gson.fromJson(json, type)
            poems.clear()
            poems.addAll(loaded)
        }

        // If all poems were deleted, restore default
        if (poems.isEmpty()) {
            loadDefaultPoem()
        }
    }

    private fun loadDefaultPoem() {
        try {
            val text = context.resources.openRawResource(
                context.resources.getIdentifier(
                    "default_custom_poem",
                    "raw",
                    context.packageName
                )
            ).bufferedReader().use { it.readText() }.trim()

            val defaultPoem = CustomPoem(
                title = "Welcome Poem",
                text = text
            )
            poems.add(defaultPoem)
            savePoems()
        } catch (e: Exception) {
            // Silently handle failure to load default poem
        }
    }

    fun savePoems() {
        val json = gson.toJson(poems)
        prefs.edit().putString(storageKey, json).apply()
    }

    fun addPoem(poem: CustomPoem) {
        if (poems.size >= maxPoems) return
        poems.add(poem)
        savePoems()
    }

    fun updatePoem(poem: CustomPoem) {
        val index = poems.indexOfFirst { it.id == poem.id }
        if (index != -1) {
            poems[index] = poem
            savePoems()
        }
    }

    fun deletePoem(poem: CustomPoem) {
        poems.removeIf { it.id == poem.id }
        savePoems()

        // If all poems deleted, restore default
        if (poems.isEmpty()) {
            loadDefaultPoem()
        }
    }

    fun duplicatePoem(poem: CustomPoem) {
        if (poems.size >= maxPoems) return

        val duplicate = CustomPoem(
            title = "${poem.title} (Copy)",
            text = poem.text
        )

        // Insert right after the original
        val index = poems.indexOfFirst { it.id == poem.id }
        if (index != -1) {
            poems.add(index + 1, duplicate)
        } else {
            poems.add(duplicate)
        }

        savePoems()
    }

    fun getRandomPoem(): CustomPoem? {
        return if (poems.isNotEmpty()) {
            poems.random()
        } else {
            null
        }
    }
}
