package com.jmisabella.zrooms

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager for custom meditations with persistence to SharedPreferences
 */
class CustomMeditationManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("custom_meditations", Context.MODE_PRIVATE)
    private val storageKey = "customMeditations"
    private val maxMeditations = 35
    private val gson = Gson()

    val meditations = mutableStateListOf<CustomMeditation>()

    val canAddMore: Boolean
        get() = meditations.size < maxMeditations

    init {
        loadMeditations()
    }

    fun loadMeditations() {
        val json = prefs.getString(storageKey, null)
        if (json != null) {
            val type = object : TypeToken<List<CustomMeditation>>() {}.type
            val loaded: List<CustomMeditation> = gson.fromJson(json, type)
            meditations.clear()
            meditations.addAll(loaded)
        }

        // If all meditations were deleted, restore default
        if (meditations.isEmpty()) {
            loadDefaultMeditation()
        }
    }

    private fun loadDefaultMeditation() {
        try {
            val text = context.resources.openRawResource(
                context.resources.getIdentifier(
                    "default_custom_meditation",
                    "raw",
                    context.packageName
                )
            ).bufferedReader().use { it.readText() }.trim()

            val defaultMeditation = CustomMeditation(
                title = "Welcome Meditation",
                text = text
            )
            meditations.add(defaultMeditation)
            saveMeditations()
        } catch (e: Exception) {
            // Silently handle failure to load default meditation
        }
    }

    fun saveMeditations() {
        val json = gson.toJson(meditations)
        prefs.edit().putString(storageKey, json).apply()
    }

    fun addMeditation(meditation: CustomMeditation) {
        if (meditations.size >= maxMeditations) return
        meditations.add(meditation)
        saveMeditations()
    }

    fun updateMeditation(meditation: CustomMeditation) {
        val index = meditations.indexOfFirst { it.id == meditation.id }
        if (index != -1) {
            meditations[index] = meditation
            saveMeditations()
        }
    }

    fun deleteMeditation(meditation: CustomMeditation) {
        meditations.removeIf { it.id == meditation.id }
        saveMeditations()

        // If all meditations deleted, restore default
        if (meditations.isEmpty()) {
            loadDefaultMeditation()
        }
    }

    fun duplicateMeditation(meditation: CustomMeditation) {
        if (meditations.size >= maxMeditations) return

        val duplicate = CustomMeditation(
            title = "${meditation.title} (Copy)",
            text = meditation.text
        )

        // Insert right after the original
        val index = meditations.indexOfFirst { it.id == meditation.id }
        if (index != -1) {
            meditations.add(index + 1, duplicate)
        } else {
            meditations.add(duplicate)
        }

        saveMeditations()
    }

    fun getRandomMeditation(): CustomMeditation? {
        return if (meditations.isNotEmpty()) {
            meditations.random()
        } else {
            null
        }
    }
}
