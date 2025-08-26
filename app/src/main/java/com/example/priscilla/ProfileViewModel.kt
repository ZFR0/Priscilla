package com.example.priscilla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.ImageUploader
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.util.Log
import com.example.priscilla.data.auth.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val imageUploader: ImageUploader,
    private val chatRepository: ChatRepository
) : ViewModel() {
    val currentUser = authRepository.currentUser

    val isModelBusy: StateFlow<Boolean> = chatRepository.isLoading
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    val userProfile: StateFlow<UserProfile> = currentUser.flatMapLatest { user ->
        profileRepository.getUserProfileFlow(user?.uid)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserProfile() // Start with a default, empty profile
    )

    fun updateProfilePicture(bitmap: Bitmap) {
        val userId = currentUser.value?.uid ?: return
        if (_isUploading.value) return

        viewModelScope.launch {
            _isUploading.value = true
            val imageUrl = imageUploader.uploadImage(bitmap)
            if (imageUrl != null) {
                profileRepository.updateUserPhotoUrl(userId, imageUrl)
            } else {
                // Optional: Show an error to the user via a SharedFlow/event
                Log.e("ProfileViewModel", "Profile picture upload failed.")
            }
            _isUploading.value = false
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun signInWithGoogleToken(tokenId: String) {
        viewModelScope.launch {
            authRepository.signInWithGoogle(tokenId)
        }
    }

    // Standard ViewModel Factory for dependency injection.
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
                val imageUploader = application.appContainer.imageUploader
                val chatRepository = application.appContainer.chatRepository
                return ProfileViewModel(authRepository, profileRepository, imageUploader, chatRepository) as T
            }
        }
    }
}