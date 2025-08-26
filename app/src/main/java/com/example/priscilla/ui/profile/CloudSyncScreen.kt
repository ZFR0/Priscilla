package com.example.priscilla.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.priscilla.CloudSyncViewModel
import com.example.priscilla.SyncUiState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.priscilla.SyncUiEvent
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    navController: NavController,
    cloudSyncViewModel: CloudSyncViewModel = viewModel(factory = CloudSyncViewModel.Factory)
) {
    val uiState by cloudSyncViewModel.uiState.collectAsState()
    val isLoading by cloudSyncViewModel.isLoading.collectAsState()
    val hasUnsyncedChanges by cloudSyncViewModel.hasUnsyncedChanges.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        cloudSyncViewModel.eventFlow.collectLatest { event ->
            when (event) {
                is SyncUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        // AnimatedContent provides a smooth transition when the state changes
        AnimatedContent(
            targetState = uiState,
            label = "SyncStateAnimation",
            modifier = Modifier.padding(paddingValues)
        ) { state ->
            // Based on the state from the ViewModel, we show a different UI
            when (state) {
                is SyncUiState.Loading -> LoadingState()
                is SyncUiState.Guest -> GuestState()
                is SyncUiState.MigrationNeeded -> MigrationState(
                    isLoading = isLoading,
                    isReturningUser = state.isReturningUser,
                    onUpload = { cloudSyncViewModel.onUploadData() },
                    onDownload = { cloudSyncViewModel.onDownloadData() },
                    onStartFresh = { cloudSyncViewModel.onStartFresh() },
                    onMerge = { cloudSyncViewModel.onMergeData() }
                )
                is SyncUiState.Synced -> SyncedState(
                    isLoading = isLoading,
                    status = state.lastSyncStatus,
                    onSyncNow = { cloudSyncViewModel.onSyncNow() },
                    isSyncButtonEnabled = hasUnsyncedChanges
                )
            }
        }
    }
}

// --- UI COMPOSABLES FOR EACH STATE ---

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GuestState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign in to your profile to enable cloud sync.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This will back up your conversations and settings, allowing you to restore them on any device.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MigrationState(
    isLoading: Boolean,
    isReturningUser: Boolean,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onStartFresh: () -> Unit,
    onMerge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val title = if (isReturningUser) "Welcome Back!" else "Account Upgrade"
        val description = if (isReturningUser) {
            "We've found existing data in your cloud account. You also have local data on this device. How would you like to proceed?"
        } else {
            "We've detected local chat history and settings from your time as a guest. Would you like to upload this data to your new account?"
        }

        Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            if (isReturningUser) {
                Button(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
                    Text("Merge Cloud & Local Data")
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep Cloud Data Only (Deletes Local)")
                }
            } else {
                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                    Text("Yes, Upload My Data")
                }
                Spacer(modifier = Modifier.height(16.dp))
                // for the "No, Start Fresh" guest upgrade option.
                OutlinedButton(onClick = onStartFresh, modifier = Modifier.fillMaxWidth()) {
                    Text("No, Start Fresh (Deletes Local Data)")
                }
            }
        }
    }
}

@Composable
private fun SyncedState(
    isLoading: Boolean,
    status: String,
    onSyncNow: () -> Unit,
    isSyncButtonEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sync Status", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        // --- MODIFY THE BUTTON'S ENABLED LOGIC ---
        Button(
            onClick = onSyncNow,
            // The button is now enabled only if there are unsynced changes AND we are not already loading.
            enabled = isSyncButtonEnabled && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Sync Now")
            }
        }
    }
}