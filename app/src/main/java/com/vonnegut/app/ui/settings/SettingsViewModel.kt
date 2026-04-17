package com.vonnegut.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vonnegut.app.VonnegutApplication
import com.vonnegut.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsState(
    val systemPrompt: String,
    val customInstructions: String,
    val userName: String,
    val userOccupation: String,
    val userLocation: String,
    val userAge: String,
    val userFamily: String,
    val userInterests: String,
    val userTone: String,
    val contextWindowLimit: Int,
    val maxResponseTokens: Int,
    val temperature: Float,
    val activeModelPath: String?,
    val modelsDirectoryPath: String,
    val sourceDirectoryUri: String?
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as VonnegutApplication).preferences

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsState> = _state

    private fun loadState() = SettingsState(
        systemPrompt = prefs.systemPrompt,
        customInstructions = prefs.customInstructions,
        userName = prefs.userName,
        userOccupation = prefs.userOccupation,
        userLocation = prefs.userLocation,
        userAge = prefs.userAge,
        userFamily = prefs.userFamily,
        userInterests = prefs.userInterests,
        userTone = prefs.userTone,
        contextWindowLimit = prefs.contextWindowLimit,
        maxResponseTokens = prefs.maxResponseTokens,
        temperature = prefs.temperature,
        activeModelPath = prefs.activeModelPath,
        modelsDirectoryPath = appModelsDirectory(),
        sourceDirectoryUri = prefs.modelSourceTreeUri
    )

    private fun appModelsDirectory(): String =
        getApplication<VonnegutApplication>()
            .getExternalFilesDir(null)
            ?.resolve("models")
            ?.absolutePath
            ?: "(unavailable)"

    fun saveAll(
        systemPrompt: String,
        customInstructions: String,
        userName: String,
        userOccupation: String,
        userLocation: String,
        userAge: String,
        userFamily: String,
        userInterests: String,
        userTone: String,
        contextWindowLimit: Int,
        maxResponseTokens: Int,
        temperature: Float
    ) {
        prefs.systemPrompt = systemPrompt
        prefs.customInstructions = customInstructions
        prefs.userName = userName
        prefs.userOccupation = userOccupation
        prefs.userLocation = userLocation
        prefs.userAge = userAge
        prefs.userFamily = userFamily
        prefs.userInterests = userInterests
        prefs.userTone = userTone
        prefs.contextWindowLimit = contextWindowLimit
        prefs.maxResponseTokens = maxResponseTokens
        prefs.temperature = temperature
        _state.value = loadState()
    }

    fun resetSystemPrompt() {
        prefs.systemPrompt = UserPreferences.DEFAULT_SYSTEM_PROMPT
        _state.value = _state.value.copy(systemPrompt = UserPreferences.DEFAULT_SYSTEM_PROMPT)
    }

    fun refresh() {
        _state.value = loadState()
    }
}
