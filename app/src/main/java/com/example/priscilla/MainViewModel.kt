package com.example.priscilla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.auth.VisualPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import android.util.Log
import com.example.priscilla.data.SettingsRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    draftVisualPreferencesFlow: StateFlow<VisualPreferences?>
) : ViewModel() {
    private val _isThemeLoading = MutableStateFlow(true)
    val isThemeLoading: StateFlow<Boolean> = _isThemeLoading.asStateFlow()

    // A private list of all possible screens to map against
    private val allScreens = listOf(Screen.Chat, Screen.History, Screen.Reminders, Screen.Settings, Screen.Profile)
    private val _navItems = MutableStateFlow(allScreens)

    val navItems: StateFlow<List<Screen>> = _navItems.asStateFlow()

    fun onNavItemMoved(fromIndex: Int, toIndex: Int) {
        // Update the state IMMEDIATELY
        val currentList = _navItems.value.toMutableList()
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)
        _navItems.value = currentList

        // Then persist to repository in background
        viewModelScope.launch {
            settingsRepository.updateNavItemOrder(currentList.map { it.route })
        }
    }

    // This is now the single source of truth for the app's theme.
    val visualPreferences: StateFlow<VisualPreferences> =
        // First, get the flow of SAVED preferences from the repository.
        authRepository.currentUser.flatMapLatest { user ->
            Log.d("ThemeDebug", "User changed. New UID: ${user?.uid}. Subscribing to saved preferences.")
            profileRepository.getUserProfileFlow(user?.uid)
                .map { it.visualPreferences } // <-- Extract the visualPreferences from the UserProfile
                .catch { exception ->
                    Log.w("ThemeDebug", "Caught an error in the saved preferences flow.", exception)
                    emit(VisualPreferences.DEFAULT)
                }
        }
            .onEach { _isThemeLoading.value = false }
            // Then, COMBINE it with the DRAFT flow.
            .combine(draftVisualPreferencesFlow) { savedPrefs, draftPrefs ->
                // The logic is simple: if a draft exists, show it. Otherwise, show the saved one.
                draftPrefs ?: savedPrefs
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = VisualPreferences.DEFAULT
            )

    init {
        // This will listen for ANY change in the saved order and update the UI state.
        viewModelScope.launch {
            settingsRepository.navItemOrderFlow.collect { savedOrder ->
                if (savedOrder.isNotEmpty()) {
                    val orderedScreens = savedOrder.mapNotNull { route ->
                        allScreens.find { it.route == route }
                    }
                    val missingScreens = allScreens.filterNot { orderedScreens.contains(it) }
                    // Update the UI state whenever the repository changes.
                    // This is what will trigger the update on login.
                    _navItems.value = orderedScreens + missingScreens
                }
            }
        }

        // Keep visualPreferences flow hot
        viewModelScope.launch {
            visualPreferences.collect()
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
                val authRepository = application.appContainer.authRepository
                val profileRepository = application.appContainer.profileRepository
                val settingsRepository = application.appContainer.settingsRepository
                val draftFlow = application.appContainer.draftVisualPreferences
                return MainViewModel(authRepository, profileRepository, settingsRepository, draftFlow) as T
            }
        }
    }
}