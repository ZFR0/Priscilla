package com.example.priscilla.data

import android.graphics.Bitmap

// This data class is used by the UI and the ViewModel,
// so it lives in a shared data package.
data class ChatTurn(
    val user: String,
    val assistant: String,
    val imagePath: String? = null, // Stores the URL or local path
    val localBitmap: Bitmap? = null // For displaying a newly selected image before sending
)