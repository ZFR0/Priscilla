// In file: app/src/main/java/com/example/priscilla/ThemeViewModel.kt

package com.example.priscilla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.auth.BorderStyle
import com.example.priscilla.data.auth.ColorPalette
import com.example.priscilla.data.auth.ShapeStyle
import com.example.priscilla.data.auth.TypographyStyle
import com.example.priscilla.data.auth.VisualPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val draftPreferencesFlow: MutableStateFlow<VisualPreferences?>
) : ViewModel() {

    val draftPreferences: StateFlow<VisualPreferences> = draftPreferencesFlow
        .map { it ?: VisualPreferences.DEFAULT }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VisualPreferences.DEFAULT
        )

    fun beginEditing() {
        viewModelScope.launch {
            // Get the current saved preferences.
            val currentSavedPrefs = authRepository.currentUser.flatMapLatest { user ->
                profileRepository.getUserProfileFlow(user?.uid)
                    .map { it.visualPreferences } // Extract the preferences from the profile
            }.first()
            // Set the draft flow's value to this, starting the editing session.
            draftPreferencesFlow.value = currentSavedPrefs
        }
    }

    // It clears the draft, reverting the app theme to the saved one.
    fun cancelEditing() {
        draftPreferencesFlow.value = null
    }

    // All update functions now modify the shared draft flow.
    fun updateColorPalette(newPalette: ColorPalette) {
        draftPreferencesFlow.update { it?.copy(colorPalette = newPalette) }
    }

    fun updateTypographyStyle(style: TypographyStyle) {
        draftPreferencesFlow.update { it?.copy(typographyStyle = style) }
    }

    fun updateShapeStyle(style: ShapeStyle) {
        draftPreferencesFlow.update { it?.copy(shapeStyle = style) }
    }

    fun updateBorderStyle(style: BorderStyle) {
        draftPreferencesFlow.update { it?.copy(borderStyle = style) }
    }

    fun saveChanges() {
        val userId = authRepository.currentUser.value?.uid ?: return
        val finalDraft = draftPreferencesFlow.value ?: return

        viewModelScope.launch {
            // Get the most recent full user profile from the repository.
            val currentProfile = profileRepository.getUserProfileFlow(userId).first()

            // Create a new UserProfile object, copying the old data but
            //    updating the visualPreferences with our new draft.
            val updatedProfile = currentProfile.copy(visualPreferences = finalDraft)

            // Save the entire updated profile.
            profileRepository.updateUserProfile(userId, updatedProfile)

            // After saving, clear the draft state. This makes the global theme
            // revert to reading the (now updated) saved preferences.
            draftPreferencesFlow.value = null
        }
    }

    // ... (Companion Object needs updating)
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication
                val profileRepository = application.appContainer.profileRepository
                val authRepository = application.appContainer.authRepository

                val draftFlow = application.appContainer.draftVisualPreferences
                return ThemeViewModel(profileRepository, authRepository, draftFlow) as T
            }
        }
    }

}