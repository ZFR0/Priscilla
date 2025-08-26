package com.example.priscilla.data.auth

// A simple data class to represent the current user's state in the app.
data class PriscillaUser(
    val uid: String, // The unique ID from Firebase Auth
    val isGuest: Boolean,
    val displayName: String?,
    val email: String?
)