package com.example.priscilla.data.repository

import android.util.Log
import com.example.priscilla.ProfileRepository
import com.example.priscilla.data.auth.BorderStyle
import com.example.priscilla.data.auth.ColorPalette
import com.example.priscilla.data.auth.LoaderStyle
import com.example.priscilla.data.auth.ShapeStyle
import com.example.priscilla.data.auth.TypographyStyle
import com.example.priscilla.data.auth.VisualPreferences
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.priscilla.data.auth.UserProfile
import com.google.firebase.firestore.ListenerRegistration

class ProfileRepositoryImpl(
    private val firestore: FirebaseFirestore
) : ProfileRepository {
    private var userProfileListener: ListenerRegistration? = null

    // This function now takes a user ID and RETURNS a Flow that can be collected.
    // It is the ViewModel's job to decide which userId to pass in.
    override fun getUserProfileFlow(userId: String?): Flow<UserProfile> {
        if (userId == null) {
            // If there's no user (guest or signed out), return a simple flow with the default preferences.
            return callbackFlow { trySend(UserProfile()); awaitClose() }
        }
        // For a registered user, return a flow that listens to their document in Firestore.
        return firestore.collection(USERS_COLLECTION).document(userId)
            .getRealtimeUserProfileFlowWithCleanup(
                onListenerAttached = { listener -> userProfileListener = listener }
            )
    }

    // This function now requires a userId to know which document to update.
    override suspend fun updateUserProfile(userId: String, profile: UserProfile) {
        try {
            // Convert the entire UserProfile to a map for Firestore.
            val profileMap = mapOf(
                PHOTO_URL_KEY to profile.photoUrl,
                PREFERENCES_KEY to mapOf(
                    COLOR_PALETTE_KEY to profile.visualPreferences.colorPalette.name,
                    TYPOGRAPHY_STYLE_KEY to profile.visualPreferences.typographyStyle.name,
                    SHAPE_STYLE_KEY to profile.visualPreferences.shapeStyle.name,
                    LOADER_STYLE_KEY to profile.visualPreferences.loaderStyle.name,
                    BORDER_STYLE_KEY to profile.visualPreferences.borderStyle.name
                )
            )
            firestore.collection(USERS_COLLECTION).document(userId)
                .set(profileMap, SetOptions.merge()).await()
            Log.d("ProfileRepository", "Successfully updated user profile for user $userId")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error updating user profile", e)
        }
    }

    override suspend fun updateUserPhotoUrl(userId: String, url: String) {
        try {
            // This performs a highly efficient, targeted update of only the photoUrl field.
            firestore.collection(USERS_COLLECTION).document(userId)
                .update(PHOTO_URL_KEY, url).await()
            Log.d("ProfileRepository", "Successfully updated photo URL for user $userId")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error updating photo URL", e)
        }
    }

    override fun onSignOut() {
        Log.d("ProfileRepository", "Sign out detected. Removing active user profile listener.")
        // If a listener is active, detach it.
        userProfileListener?.remove()
        // Set it to null to prevent memory leaks.
        userProfileListener = null
    }

    // Data keys for our Firestore document
    companion object {
        const val USERS_COLLECTION = "users"
        const val PHOTO_URL_KEY = "photoUrl" // <-- New key
        const val PREFERENCES_KEY = "visual_preferences"
        const val COLOR_PALETTE_KEY = "color_palette"
        const val TYPOGRAPHY_STYLE_KEY = "typography_style"
        const val SHAPE_STYLE_KEY = "shape_style"
        const val LOADER_STYLE_KEY = "loader_style"
        const val BORDER_STYLE_KEY = "border_style"
    }
}

private fun com.google.firebase.firestore.DocumentReference.getRealtimeUserProfileFlowWithCleanup(
    onListenerAttached: (ListenerRegistration) -> Unit
): Flow<UserProfile> =
    callbackFlow {
        val listenerRegistration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ProfileRepository", "Listen failed.", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toUserProfile() // We will create this helper next
                trySend(profile).isSuccess
            } else {
                // If the document doesn't exist, send a default profile
                trySend(UserProfile()).isSuccess
            }
        }
        onListenerAttached(listenerRegistration)

        awaitClose {
            Log.d("ProfileRepository", "UserProfileFlow coroutine cancelled. Removing listener.")
            listenerRegistration.remove()
        }
    }

private fun DocumentSnapshot.toUserProfile(): UserProfile {
    return try {
        val photoUrl = getString(ProfileRepositoryImpl.PHOTO_URL_KEY)

        // Reuse the logic for parsing visual preferences
        val prefsMap = get(ProfileRepositoryImpl.PREFERENCES_KEY) as? Map<*, *>
        val visualPrefs = if (prefsMap != null) {
            VisualPreferences(
                colorPalette = ColorPalette.valueOf(prefsMap[ProfileRepositoryImpl.COLOR_PALETTE_KEY] as? String ?: "SYSTEM"),
                typographyStyle = TypographyStyle.valueOf(prefsMap[ProfileRepositoryImpl.TYPOGRAPHY_STYLE_KEY] as? String ?: "ROYAL_DECREE"),
                shapeStyle = ShapeStyle.valueOf(prefsMap[ProfileRepositoryImpl.SHAPE_STYLE_KEY] as? String ?: "IMPERIAL"),
                loaderStyle = LoaderStyle.valueOf(prefsMap[ProfileRepositoryImpl.LOADER_STYLE_KEY] as? String ?: "CHIBI"),
                borderStyle = BorderStyle.valueOf(prefsMap[ProfileRepositoryImpl.BORDER_STYLE_KEY] as? String ?: "DEFAULT")
            )
        } else {
            VisualPreferences.DEFAULT
        }

        UserProfile(photoUrl = photoUrl, visualPreferences = visualPrefs)

    } catch (e: Exception) {
        Log.e("ProfileRepository", "Error parsing user profile from snapshot", e)
        UserProfile() // Return default on failure
    }
}
