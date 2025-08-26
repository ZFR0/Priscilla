package com.example.priscilla.ui.profile

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.priscilla.ProfileViewModel
import com.stevdzasan.onetap.OneTapSignInWithGoogle
import com.stevdzasan.onetap.rememberOneTapSignInState
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.priscilla.ui.theme.drawCustomFrame
import com.example.priscilla.ui.theme.FrameStyles
import com.example.priscilla.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.priscilla.R
import com.example.priscilla.Screen
import com.example.priscilla.data.auth.BorderStyle

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, navController: NavController) {
    val user by viewModel.currentUser.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val oneTapState = rememberOneTapSignInState()
    val isModelBusy by viewModel.isModelBusy.collectAsState()

    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
                viewModel.updateProfilePicture(bitmap)
            }
        }
    )

    OneTapSignInWithGoogle(
        state = oneTapState,
        clientId = "426556454782-bu5s1ptpn9o3b3mvd474f5gea9bga33s.apps.googleusercontent.com",
        onTokenIdReceived = { tokenId ->
            Log.d("AuthTest", "ProfileScreen: onTokenIdReceived. Token starts with: ${tokenId.take(10)}")
            viewModel.signInWithGoogleToken(tokenId)
        },
        onDialogDismissed = { message ->
            Log.w("AuthTest", "ProfileScreen: onDialogDismissed. Message: $message")
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (user == null) {
            CircularProgressIndicator()
        } else {
            ProfileHeader(
                isGuest = user!!.isGuest,
                displayName = user!!.displayName,
                email = user!!.email,
                photoUrl = userProfile.photoUrl,
                isUploading = isUploading,
                onImageClick = { galleryLauncher.launch("image/*") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (user!!.isGuest) {
                Button(
                    onClick = { oneTapState.open() },
                    enabled = !isModelBusy
                ) {
                    Text("Sign in with Google to enable cloud features")
                }
            } else {
                Button(
                    onClick = { viewModel.signOut() },
                    enabled = !isModelBusy
                ) {
                    Text("Sign Out")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Pass the navigation lambda to SettingsOptions
            SettingsOptions(
                isGuest = user!!.isGuest,
                onThemeClicked = {
                    navController.navigate(Screen.ThemeCustomization.route)
                },
                onCloudSyncClicked = {
                    navController.navigate(Screen.CloudSync.route)
                },
                onVoiceOptionsClicked = {
                    navController.navigate(Screen.VoiceOptions.route)
                }
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    isGuest: Boolean,
    displayName: String?,
    email: String?,
    photoUrl: String?,
    isUploading: Boolean,
    onImageClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val clickableModifier = if (!isGuest) {
                Modifier.clickable(enabled = !isUploading) { onImageClick() }
            } else {
                Modifier
            }

            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .then(clickableModifier), // Apply clickable modifier
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.icon_splash_final), // Default guest icon
                error = painterResource(id = R.drawable.icon_splash_final) // Icon to show on load error
            )

            // Show a progress indicator while uploading
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(100.dp))
            }
        }
        val headerText = when {
            isGuest -> "Guest User"
            !displayName.isNullOrBlank() -> displayName // Use the name if available
            !email.isNullOrBlank() -> email             // Fallback to the email
            else -> "Registered User"                   // Final fallback just in case
        }

        Text(
            text = headerText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsOptions(
    isGuest: Boolean,
    onThemeClicked: () -> Unit,
    onCloudSyncClicked: () -> Unit,
    onVoiceOptionsClicked: () -> Unit
) {
    // Get the MainViewModel to access the current visual preferences
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val preferences by mainViewModel.visualPreferences.collectAsState()
    val currentBorderStyle = preferences.borderStyle

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        // The "App Theme" row will now dynamically check the style
        SettingsRow(
            title = "App Theme",
            subtitle = "Change the look and feel",
            isGuest = isGuest,
            requiresLogin = false,
            onClick = onThemeClicked,
            borderStyle = currentBorderStyle
        )

        // The other two rows will always be default
        SettingsRow(
            title = "Cloud Sync",
            subtitle = "Save conversations & reminders online",
            isGuest = isGuest,
            requiresLogin = true,
            borderStyle = currentBorderStyle,
            onClick = onCloudSyncClicked
        )
        SettingsRow(
            title = "Voice Options",
            subtitle = "Select Priscilla's voice",
            isGuest = isGuest,
            requiresLogin = true,
            borderStyle = currentBorderStyle,
            onClick = onVoiceOptionsClicked
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    isGuest: Boolean,
    requiresLogin: Boolean,
    onClick: (() -> Unit)? = null,
    borderStyle: BorderStyle
) {
    val isEnabled = !isGuest || !requiresLogin
    val isClickable = isEnabled && (onClick != null)

    val baseModifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = isClickable) { onClick?.invoke() }

    val finalModifier = when (borderStyle) {
        BorderStyle.RED_JEWEL -> {
            baseModifier
                .padding(vertical = 12.dp)
                .drawCustomFrame(style = FrameStyles.NOBLE_RED_JEWEL)
        }
        BorderStyle.SHARP_GREEN -> {
            baseModifier
                .padding(vertical = 12.dp)
                .drawCustomFrame(style = FrameStyles.SHARP_GREEN_JEWEL)
        }
        BorderStyle.BLUE_DROPLET -> {
            baseModifier
                .padding(vertical = 12.dp)
                .drawCustomFrame(style = FrameStyles.BLUE_DROPLET_FRAME)
        }
        else -> { // This handles BorderStyle.DEFAULT
            baseModifier
                .border(
                    width = 1.dp,
                    color = if (isEnabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                )
        }
    }
    // This is the content that will be drawn first.
    Row(
        modifier = finalModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier
            .weight(1f)
            // Reduce the padding to give the frame more space to draw
            .padding(horizontal = 24.dp, vertical = 16.dp)) {
            val textColor =
                if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.5f
                )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (requiresLogin && isGuest) {
                Text(
                    text = "Login required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (isClickable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate to $title settings"
            )
        }
    }
}