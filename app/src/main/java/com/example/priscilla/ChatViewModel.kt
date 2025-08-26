package com.example.priscilla

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.ExtractedIntent
import com.example.priscilla.data.IntentParser
import com.example.priscilla.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // --- State exposed to the UI ---
    // The ViewModel now gets its state directly from the Repository's StateFlows.
    val uiChatHistory = chatRepository.uiChatHistory
    val isInitialized = chatRepository.isModelInitialized
    val responseText = chatRepository.modelResponseText
    // Note: The global LlamaModelState.isLoading is no longer used here.
    // We get the loading state from the repository, the single source of truth.
    val isLoading = chatRepository.isLoading
    private var sendMessageJob: Job? = null
    val isTtsSpeaking = chatRepository.isTtsSpeaking

    // --- State managed ONLY by the ViewModel (UI-specific) ---
    val prompt = mutableStateOf("")
    private val promptInputFlow = MutableStateFlow("")
    private val _detectedIntent = MutableStateFlow<ExtractedIntent?>(null)
    val detectedIntent: StateFlow<ExtractedIntent?> = _detectedIntent.asStateFlow()

    // --- Intent parsing is a UI concern (showing hints), so it stays here for now ---
    private val intentParser = IntentParser()

    init {
        // Debounced intent parsing for the UI hint
        viewModelScope.launch {
            // Combine the text input flow with the settings flow.
            // This will re-evaluate whenever the text OR the setting changes.
            promptInputFlow
                .debounce(400)
                .combine(settingsRepository.smartPriscillaEnabledFlow) { text, isSmartEnabled ->
                    // We package the results into a Pair to pass them along
                    Pair(text, isSmartEnabled)
                }
                .collect { (text, isSmartEnabled) ->
                    if (isSmartEnabled && text.isNotBlank()) {
                        val intents = withContext(Dispatchers.Default) {
                            intentParser.parse(text)
                        }
                        _detectedIntent.value = intents.firstOrNull()
                    } else {
                        // If the feature is disabled OR the text is blank, clear the intent hint.
                        _detectedIntent.value = null
                    }
                }
        }
    }

    // --- Events from the UI ---

    fun onPromptChanged(newText: String) {
        prompt.value = newText
        promptInputFlow.value = newText
    }

    fun sendMessage(userMessage: String, image: Bitmap?) {
        _detectedIntent.value = null
        onPromptChanged("")

        viewModelScope.launch {
            chatRepository.sendMessage(userMessage, image)
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            chatRepository.startNewChat()
        }
    }

    // --- ViewModel Factory ---
    // This is the standard, modern way to create a ViewModel with dependencies.
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // Get the Application object from extras
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication

                // Get the repository from the application's AppContainer
                val chatRepository = application.appContainer.chatRepository
                val settingsRepository = application.appContainer.settingsRepository

                return ChatViewModel(chatRepository, settingsRepository) as T
            }
        }
    }
}