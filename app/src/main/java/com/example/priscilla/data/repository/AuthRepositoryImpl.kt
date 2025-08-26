package com.example.priscilla.data.repository

import android.util.Log
import com.example.priscilla.AuthRepository
import com.example.priscilla.data.auth.PriscillaUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import com.example.priscilla.UserScopeCleanup

class AuthRepositoryImpl(
    private val auth: FirebaseAuth
) : AuthRepository {

    private val userScopeCleanups = mutableListOf<UserScopeCleanup>()
    private val _currentUser = MutableStateFlow<PriscillaUser?>(null)
    override val currentUser: StateFlow<PriscillaUser?> = _currentUser.asStateFlow()

    init {
        // When the repository is created, start listening to auth state changes.
        // Also, check if a user is already signed in (anonymous or otherwise).
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            Log.d("AuthTest", "AuthStateListener fired. User is now: ${firebaseUser?.uid}, isAnonymous: ${firebaseUser?.isAnonymous}")
            if (firebaseUser == null) {
                // User is signed out or no user yet.
                // Try to sign in anonymously.
                signInAnonymously()
            } else {
                // A user exists. Map it to our PriscillaUser and emit it.
                _currentUser.value = mapFirebaseUserToPriscillaUser(firebaseUser)
            }
        }
    }

    override fun registerUserScopeCleanup(cleanup: UserScopeCleanup) {
        userScopeCleanups.add(cleanup)
    }

    private fun signInAnonymously() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("AuthRepository", "Anonymous sign-in SUCCESS.")
                // The AuthStateListener will fire and update our _currentUser flow.
            } else {
                Log.e("AuthRepository", "Anonymous sign-in FAILED", task.exception)
                // Handle failure, maybe retry or show an error.
                _currentUser.value = null // Explicitly set to null on failure.
            }
        }
    }

    override suspend fun signInWithGoogle(tokenId: String): Result<Unit> {
        try {
            val googleCredential = GoogleAuthProvider.getCredential(tokenId, null)
            val currentUser = auth.currentUser

            if (currentUser != null && currentUser.isAnonymous) {
                Log.d("AuthTest", "AuthRepository: Current user is anonymous. Attempting to link...")
                try {
                    // --- PRIMARY PATH: Try to link the new credential ---
                    val linkResult = currentUser.linkWithCredential(googleCredential).await()
                    val linkedUser = linkResult.user
                    if (linkedUser != null) {
                        _currentUser.value = mapFirebaseUserToPriscillaUser(linkedUser)
                        Log.d("AuthTest", "AuthRepository: LINKING SUCCEEDED. User upgraded.")
                    }
                } catch (e: FirebaseAuthUserCollisionException) {
                    // --- COLLISION PATH: This is a returning registered user ---
                    Log.w("AuthTest", "AuthRepository: User collision detected. This is a returning user. Signing in directly.")

                    // Sign in with the Google credential. This will switch from the anonymous
                    // user to their existing permanent account.
                    val signInResult = auth.signInWithCredential(googleCredential).await()
                    val signedInUser = signInResult.user
                    if (signedInUser != null) {
                        _currentUser.value = mapFirebaseUserToPriscillaUser(signedInUser)
                        Log.d("AuthTest", "AuthRepository: DIRECT SIGN-IN SUCCEEDED.")
                    }

                    // (Optional but good practice) Delete the now-orphaned anonymous user.
                    // This prevents clutter in Firebase auth list.
                    Log.d("AuthTest", "AuthRepository: Deleting orphaned anonymous user: ${currentUser.uid}")
                    currentUser.delete().await()
                }
            } else {
                // This path is for cases where there is no user, or the user is already registered.
                // Just sign in normally.
                Log.d("AuthTest", "AuthRepository: No anonymous user, signing in directly.")
                val signInResult = auth.signInWithCredential(googleCredential).await()
                val signedInUser = signInResult.user
                if (signedInUser != null) {
                    _currentUser.value = mapFirebaseUserToPriscillaUser(signedInUser)
                }
            }

            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e("AuthTest", "AuthRepository: signInWithGoogle FAILED at top level.", e)
            return Result.failure(e)
        }
    }

    override fun signOut() {
        Log.i("AuthRepository", "Sign-out initiated. Cleaning up user-scoped resources...")

        // Step 1: Tell all listeners to clean up their resources *before* we sign out.
        userScopeCleanups.forEach { it.onSignOut() }

        // Step 2: Now that listeners are detached, it's safe to sign out.
        auth.signOut()

        // The AuthStateListener will still fire and handle the transition to guest mode.
    }

    // A helper function to convert the FirebaseUser object to our app's user model.
    private fun mapFirebaseUserToPriscillaUser(firebaseUser: FirebaseUser): PriscillaUser {
        return PriscillaUser(
            uid = firebaseUser.uid,
            isGuest = firebaseUser.isAnonymous,
            displayName = firebaseUser.displayName,
            email = firebaseUser.email
        )
    }
}