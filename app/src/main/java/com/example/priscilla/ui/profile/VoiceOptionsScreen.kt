package com.example.priscilla.ui.profile

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.priscilla.VoiceOptionsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOptionsScreen(
    navController: NavController,
    viewModel: VoiceOptionsViewModel = viewModel(factory = VoiceOptionsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Options") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section for Sliders
            item {
                Column {
                    Text("Voice Speed", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = uiState.speed,
                        onValueChange = { viewModel.onSpeedChanged(it) },
                        valueRange = 0.5f..2.0f
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Voice Pitch", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = uiState.pitch,
                        onValueChange = { viewModel.onPitchChanged(it) },
                        valueRange = 0.5f..2.0f
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.previewVoice() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedVoiceId != "NONE"
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preview Voice")
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            // Section for Voice List
            item {
                Text(
                    "Select Voice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // "None" option
            item {
                VoiceListItem(
                    displayName = "None (TTS Disabled)",
                    isSelected = uiState.selectedVoiceId == "NONE",
                    onClick = { viewModel.onVoiceSelected("NONE") }
                )
            }

            // List of available system voices
            items(uiState.availableVoices, key = { it.name }) { voice ->
                VoiceListItem(
                    displayName = formatVoiceName(voice),
                    isSelected = uiState.selectedVoiceId == voice.name,
                    onClick = { viewModel.onVoiceSelected(voice.name) }
                )
            }
        }
    }
}

@Composable
private fun VoiceListItem(
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(displayName) },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

// Helper function to make voice names more readable
private fun formatVoiceName(voice: Voice): String {
    // Example voice.name: "en-us-x-sfg#male_2-local"
    // Example locale: "en_US"
    val locale = voice.locale
    val name = voice.name
    val gender = if (name.contains("male")) "Male" else if (name.contains("female")) "Female" else ""

    return "${locale.displayLanguage} - ${locale.displayCountry} ($gender)".replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
            Locale.getDefault()
        ) else it.toString()
    }
}