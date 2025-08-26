package com.example.priscilla

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.ReminderRepository
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.auth.PriscillaUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.priscilla.data.SyncStateRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// A sealed class to represent the different states of the Cloud Sync UI
sealed class SyncUiState {
    // Initial loading state
    data object Loading : SyncUiState()

    // State for a guest user
    data object Guest : SyncUiState()

    // The most important state: when a user has just logged in and we need their decision
    data class MigrationNeeded(val isReturningUser: Boolean) : SyncUiState()

    // The standard state for a logged-in user who is fully synced or has local changes
    data class Synced(val lastSyncStatus: String) : SyncUiState()
}

sealed class SyncUiEvent {
    data class ShowSnackbar(val message: String) : SyncUiEvent()
}

class CloudSyncViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasUnsyncedChanges = MutableStateFlow(false)
    val hasUnsyncedChanges: StateFlow<Boolean> = _hasUnsyncedChanges.asStateFlow()

    private val _eventFlow = MutableSharedFlow<SyncUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        // This is the core logic engine for the ViewModel.
        // It listens to changes in the user's authentication state.
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                determineSyncState(user)
            }
        }
    }

    private fun determineSyncState(user: PriscillaUser?) {
        viewModelScope.launch {
            _isLoading.value = true
            when {
                user == null -> {
                    _uiState.value = SyncUiState.Loading
                    _hasUnsyncedChanges.value = false // Reset on logout
                }
                user.isGuest -> {
                    _uiState.value = SyncUiState.Guest
                    _hasUnsyncedChanges.value = false // Reset for guest
                }
                else -> {
                    val cloudTimestamp = syncStateRepository.getCloudLastSyncTimestamp(user.uid)

                    // --- CHECK ALL THREE REPOSITORIES ---
                    val hasUnsyncedChats = chatRepository.hasUnsyncedData(cloudTimestamp)
                    val hasUnsyncedSettings = settingsRepository.hasUnsyncedData(cloudTimestamp)
                    val hasUnsyncedReminders = reminderRepository.hasUnsyncedData(cloudTimestamp)
                    val hasUnsyncedLocalChanges = hasUnsyncedChats || hasUnsyncedSettings || hasUnsyncedReminders

                    _hasUnsyncedChanges.value = hasUnsyncedLocalChanges
                    val hasCloudData = cloudTimestamp > 0L

                    // Update the status text for the Synced state
                    val statusText = if (hasUnsyncedLocalChanges) {
                        "You have local changes to sync."
                    } else {
                        "Data is up to date."
                    }

                    _uiState.value = when {
                        hasUnsyncedLocalChanges && hasCloudData -> SyncUiState.MigrationNeeded(isReturningUser = true)
                        hasUnsyncedLocalChanges && !hasCloudData -> SyncUiState.MigrationNeeded(isReturningUser = false)
                        else -> SyncUiState.Synced(statusText)
                    }
                }
            }
            _isLoading.value = false
        }
    }

    // --- User Actions ---

    // The "Yes, Upload My Data" button for a GUEST UPGRADE calls this.
    fun onUploadData() {
        val user = authRepository.currentUser.value ?: return
        if (user.isGuest) return

        viewModelScope.launch {
            _isLoading.value = true
            val chatResult = chatRepository.uploadUnsyncedData(user.uid, 0L)
            val settingsResult = settingsRepository.uploadUnsyncedSettings(user.uid, 0L)
            val remindersResult = reminderRepository.uploadUnsyncedReminders(user.uid, 0L)

            if (chatResult.success && settingsResult.success && remindersResult.success) {
                syncStateRepository.updateLastSyncTimestamp(user.uid, System.currentTimeMillis())

                // --- BUILD THE DETAILED SUCCESS MESSAGE ---
                val messages = mutableListOf<String>()
                if (chatResult.uploaded > 0) messages.add("${chatResult.uploaded} conversations")
                if (settingsResult.uploaded > 0) messages.add("settings")
                if (remindersResult.uploaded > 0) messages.add("${remindersResult.uploaded} reminders")

                val status = if (messages.isNotEmpty()) {
                    "Successfully uploaded ${messages.joinToString(", ")}."
                } else {
                    "Your data is already up to date."
                }
                _uiState.value = SyncUiState.Synced(status)

            } else {
                _uiState.value = SyncUiState.Synced("Upload failed. Please try again.")
                _eventFlow.emit(SyncUiEvent.ShowSnackbar("Upload failed. Please check your connection and try again."))
            }
            _isLoading.value = false
            determineSyncState(authRepository.currentUser.value)
        }
    }

    fun onDownloadData() {
        val user = authRepository.currentUser.value ?: return
        if (user.isGuest) return

        viewModelScope.launch {
            _isLoading.value = true
            Log.d("SYNC_DEBUG", "CloudSyncViewModel: onDownloadData() started.")
            // Call the robust migration functions in all repositories.
            chatRepository.migrateCloudToLocal(user.uid)
            settingsRepository.migrateCloudToLocal(user.uid)
            reminderRepository.migrateCloudToLocal(user.uid)

            // After all migrations are complete, update the sync timestamp.
            syncStateRepository.updateLastSyncTimestamp(user.uid, System.currentTimeMillis())
            _uiState.value = SyncUiState.Synced("Data downloaded successfully.")

            // Check the state again to update the UI correctly.
            determineSyncState(user)
            _isLoading.value = false
        }
    }

    // The "Merge" button and "Sync Now" button will call this.
    fun onMergeData() {
        val user = authRepository.currentUser.value ?: return
        if (user.isGuest) return

        viewModelScope.launch {
            _isLoading.value = true
            val lastSyncTimestamp = syncStateRepository.getCloudLastSyncTimestamp(user.uid)

            val chatResult = chatRepository.uploadUnsyncedData(user.uid, lastSyncTimestamp)
            val settingsResult = settingsRepository.uploadUnsyncedSettings(user.uid, lastSyncTimestamp)
            val remindersResult = reminderRepository.uploadUnsyncedReminders(user.uid, lastSyncTimestamp)

            if (chatResult.success && settingsResult.success && remindersResult.success) {
                syncStateRepository.updateLastSyncTimestamp(user.uid, System.currentTimeMillis())

                // --- BUILD THE DETAILED SUCCESS MESSAGE ---
                val messages = mutableListOf<String>()
                if (chatResult.uploaded > 0) messages.add("${chatResult.uploaded} conversations")
                // For settings, "1" isn't descriptive, so we just say "settings"
                if (settingsResult.uploaded > 0) messages.add("settings")
                if (remindersResult.uploaded > 0) messages.add("${remindersResult.uploaded} reminders")

                val status = if (messages.isNotEmpty()) {
                    "Successfully synced ${messages.joinToString(", ")}."
                } else {
                    "Your data is already up to date."
                }
                _uiState.value = SyncUiState.Synced(status)

            } else {
                _uiState.value = SyncUiState.Synced("Merge failed. Please try again.")
                _eventFlow.emit(SyncUiEvent.ShowSnackbar("Sync failed. Please check your connection and try again."))
            }
            _isLoading.value = false
            determineSyncState(authRepository.currentUser.value)
        }
    }

    fun onStartFresh() {
        viewModelScope.launch {
            _isLoading.value = true
            // Clear ALL local data ---
            chatRepository.clearLocalChatData()
            settingsRepository.clearLocalSettings()
            reminderRepository.clearLocalReminders()

            _uiState.value = SyncUiState.Synced("Ready to sync.")
            _isLoading.value = false
        }
    }

    fun onSyncNow() {
        // The logic for a manual sync is the same as a merge.
        // It uploads local changes and relies on the real-time listener
        // to download any cloud changes.
        onMergeData()
    }

    // Standard ViewModel Factory for dependency injection
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication
                val authRepository = application.appContainer.authRepository
                val chatRepository = application.appContainer.chatRepository
                val syncStateRepository = application.appContainer.syncStateRepository
                val settingsRepository = application.appContainer.settingsRepository
                val reminderRepository = application.appContainer.reminderRepository
                return CloudSyncViewModel(authRepository, chatRepository, syncStateRepository, settingsRepository, reminderRepository) as T
            }
        }
    }
}