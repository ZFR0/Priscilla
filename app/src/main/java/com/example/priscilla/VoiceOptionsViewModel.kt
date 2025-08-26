package com.example.priscilla

import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.TtsManager
import com.example.priscilla.data.VoiceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

// A data class to hold all the UI state for the Voice Options screen
data class VoiceOptionsUiState(
    val availableVoices: List<Voice> = emptyList(),
    val selectedVoiceId: String = "NONE",
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f
)

class VoiceOptionsViewModel(
    private val ttsManager: TtsManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // --- State exposed to the UI ---
    val uiState: StateFlow<VoiceOptionsUiState> =
        settingsRepository.voiceSettingsFlow.map { savedSettings ->
            val allVoices = ttsManager.getAvailableVoices()

            val filteredVoices = allVoices.filter { voice ->
                voice.locale == Locale.US
            }.sortedBy { it.name }

            VoiceOptionsUiState(
                availableVoices = filteredVoices,
                selectedVoiceId = savedSettings.selectedVoiceId,
                pitch = savedSettings.pitch,
                speed = savedSettings.speed
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VoiceOptionsUiState()
        )

    // --- User Actions ---

    fun onVoiceSelected(voiceId: String) {
        viewModelScope.launch {
            val newSettings = uiState.value.copy(selectedVoiceId = voiceId)
            settingsRepository.updateVoiceSettings(newSettings.toVoiceSettings())
        }
    }

    fun onPitchChanged(pitch: Float) {
        viewModelScope.launch {
            val newSettings = uiState.value.copy(pitch = pitch)
            settingsRepository.updateVoiceSettings(newSettings.toVoiceSettings())
        }
    }

    fun onSpeedChanged(speed: Float) {
        viewModelScope.launch {
            val newSettings = uiState.value.copy(speed = speed)
            settingsRepository.updateVoiceSettings(newSettings.toVoiceSettings())
        }
    }

    fun previewVoice() {
        viewModelScope.launch {
            val settings = uiState.value
            ttsManager.setSpeechParameters(settings.selectedVoiceId, settings.pitch, settings.speed)
            ttsManager.speakStream("You dare to address me, commoner?")
        }
    }

    // Helper function to convert UI state to the data model for saving
    private fun VoiceOptionsUiState.toVoiceSettings(): VoiceSettings {
        return VoiceSettings(
            selectedVoiceId = this.selectedVoiceId,
            pitch = this.pitch,
            speed = this.speed
        )
    }

    // --- ViewModel Factory ---
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication
                val ttsManager = application.appContainer.ttsManager
                val settingsRepository = application.appContainer.settingsRepository
                return VoiceOptionsViewModel(ttsManager, settingsRepository) as T
            }
        }
    }
}