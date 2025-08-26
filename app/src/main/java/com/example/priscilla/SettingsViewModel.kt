package com.example.priscilla

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.DownloadState
import com.example.priscilla.data.LlamaModel
import com.example.priscilla.data.ModelDownloader
import com.example.priscilla.data.ModelParameters
import com.example.priscilla.data.NetworkMonitor
import com.example.priscilla.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.priscilla.data.PermissionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class SettingsUiEvent {
    data class ShowToast(val message: String) : SettingsUiEvent()
}

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val modelDownloader = ModelDownloader(application)

    private val permissionManager = PermissionManager(application)

    private val networkMonitor = NetworkMonitor(application)

    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // This StateFlow will hold the real-time permission status.
    private val _arePermissionsGranted = MutableStateFlow(permissionManager.areAllSmartPermissionsGranted())
    private val arePermissionsGranted: StateFlow<Boolean> = _arePermissionsGranted.asStateFlow()

    // This is the user's SAVED PREFERENCE (e.g., from the cloud).
    val smartPriscillaPreference: StateFlow<Boolean> = settingsRepository.smartPriscillaEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _eventFlow = MutableSharedFlow<SettingsUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun onSmartSwitchClick() {
        viewModelScope.launch {
            // This function is called when the user clicks the row.
            // We check the conditions IF the switch is currently OFF.
            if (!isSmartPriscillaTrulyEnabled.value) {
                if (!arePermissionsGranted.value) {
                    _eventFlow.emit(SettingsUiEvent.ShowToast("Permissions are required to enable this."))
                } else if (!isOnline.value) {
                    _eventFlow.emit(SettingsUiEvent.ShowToast("An internet connection is required to enable this."))
                }
            }
        }
    }

    // This is the final state for the UI Switch. It's ON only if both are true.
    val isSmartPriscillaTrulyEnabled: StateFlow<Boolean> =
        combine(smartPriscillaPreference, arePermissionsGranted, isOnline) { preference, hasPermission, hasInternet ->
            Log.d("SMART_DEBUG", "Combining: preference=$preference, hasPermission=$hasPermission, hasInternet=$hasInternet -> final=${preference && hasPermission && hasInternet}")
            preference && hasPermission && hasInternet
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    val modelParameters: StateFlow<ModelParameters> = settingsRepository.modelParametersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.DEFAULTS
        )

    // This now gets the loading state from the single source of truth: the repository.
    val isModelBusy: StateFlow<Boolean> = chatRepository.isLoading

    val selectedModel: StateFlow<LlamaModel> = settingsRepository.selectedModelFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LlamaModel.TinyLlama1B
        )

    fun onModelSelected(model: LlamaModel) {
        viewModelScope.launch {
            settingsRepository.updateSelectedModel(model)
        }
    }

    fun startModelDownload(model: LlamaModel) {
        if (_downloadState.value is DownloadState.Downloading) return

        viewModelScope.launch {
            modelDownloader.downloadModel(model).collect { state ->
                _downloadState.value = state
            }
        }
    }

    suspend fun updateParameters(params: ModelParameters) {
        settingsRepository.updateParameters(params)
    }

    fun updateUserPreference(isEnabled: Boolean) {
        Log.d("PERMISSION_DEBUG", "ViewModel: updateUserPreference called with: $isEnabled")
        viewModelScope.launch {
            settingsRepository.updateSmartPriscillaEnabled(isEnabled)
        }
    }

    // Call this after the permission dialog is dismissed to refresh the state.
    fun refreshPermissionsStatus() {
        val newStatus = permissionManager.areAllSmartPermissionsGranted()
        Log.d("PERMISSION_DEBUG", "ViewModel: refreshPermissionsStatus called. New status: $newStatus")
        _arePermissionsGranted.value = newStatus
    }

    fun triggerModelReload() {
        viewModelScope.launch {
            // It can directly call the repository's public method.
            chatRepository.triggerModelReload()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication
                // Get the singleton instance from the AppContainer.
                // It already has all the correct dependencies (authRepo, cloudDataSource).
                val settingsRepository = application.appContainer.settingsRepository
                val chatRepository = application.appContainer.chatRepository

                val permissionManager = (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PriscillaApplication).let { PermissionManager(it) }

                return SettingsViewModel(application, settingsRepository, chatRepository) as T
            }
        }
    }
}