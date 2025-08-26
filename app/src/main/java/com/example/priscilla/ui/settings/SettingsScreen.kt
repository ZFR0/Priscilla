package com.example.priscilla.ui.settings

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.priscilla.SettingsViewModel
import com.example.priscilla.data.DownloadState
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.availableModels
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import com.example.priscilla.data.PermissionManager
import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.priscilla.SettingsUiEvent
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onReloadComplete: () -> Unit
) {
    val parameters by settingsViewModel.modelParameters.collectAsState()
    val isModelBusy by settingsViewModel.isModelBusy.collectAsState()
    val selectedModel by settingsViewModel.selectedModel.collectAsState()
    val downloadState by settingsViewModel.downloadState.collectAsState()
    val isSmartPriscillaTrulyEnabled by settingsViewModel.isSmartPriscillaTrulyEnabled.collectAsState()
    val context = LocalContext.current // Get context for the PermissionManager

    LaunchedEffect(key1 = true) {
        settingsViewModel.eventFlow.collectLatest { event ->
            when (event) {
                is SettingsUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val activity = context as ComponentActivity // Cast context to Activity for permission check
    var showPermissionDialog by remember { mutableStateOf(false) }

    // This effect runs when the user returns to this screen (e.g., from system settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.refreshPermissionsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = { Text("You have permanently denied a required permission. To enable Smart Priscilla, please grant the permissions in the system settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        // Create an intent that opens the app's specific settings page
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("PERMISSION_DEBUG", "Permission dialog closed. Results: $permissions")
        // Check if ALL requested permissions were granted.
        val allPermissionsGranted = permissions.values.all { it }
        Log.d("PERMISSION_DEBUG", "Were all permissions granted? -> $allPermissionsGranted")
        if (allPermissionsGranted) {
            // If yes, update the user's PREFERENCE to ON.
            Log.d("PERMISSION_DEBUG", "All granted. Setting user preference to ON.")
            settingsViewModel.updateUserPreference(true)
        } else {
            // If not all permissions are granted, do nothing to the user's preference.
            // The feature will remain disabled because the permission check will fail,
            // which is the correct behavior. This prevents us from overriding a
            // 'true' preference synced from the cloud.
            Log.d("PERMISSION_DEBUG", "Not all permissions were granted by the user. User preference will not be changed.")
        }
        // Refresh the ViewModel's permission state after the dialog closes.
        Log.d("PERMISSION_DEBUG", "Calling refreshPermissionsStatus()")
        settingsViewModel.refreshPermissionsStatus()
    }

    var temp by remember(parameters.temp) { mutableFloatStateOf(parameters.temp) }
    var topK by remember(parameters.topK) { mutableStateOf(parameters.topK.toString()) }
    var topP by remember(parameters.topP) { mutableFloatStateOf(parameters.topP) }
    var repeatPenalty by remember(parameters.repeatPenalty) { mutableFloatStateOf(parameters.repeatPenalty) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("General Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // First, call our new ViewModel function. It will check the
                        // current state and decide if a toast is needed.
                        settingsViewModel.onSmartSwitchClick()
                        Log.d("PERMISSION_DEBUG", "Row clicked. isSmartPriscillaTrulyEnabled = $isSmartPriscillaTrulyEnabled")
                        if (isSmartPriscillaTrulyEnabled) {
                            // If the feature is currently ON, clicking turns the PREFERENCE OFF.
                            Log.d("PERMISSION_DEBUG", "Action: Turning preference OFF.")
                            settingsViewModel.updateUserPreference(false)
                        } else {
                            // If the feature is OFF, we have two cases:
                            val permissionManager = PermissionManager(context)
                            if (permissionManager.isAnyPermissionPermanentlyDenied(activity)) {
                                // CASE 1: Permanently denied. Show our helpful dialog.
                                Log.d("PERMISSION_DEBUG", "Action: Permissions permanently denied. Showing custom dialog.")
                                showPermissionDialog = true
                            } else {
                                // CASE 2: Not yet permanently denied. Show the system dialog.
                                Log.d("PERMISSION_DEBUG", "Action: Launching system permission request.")
                                permissionLauncher.launch(permissionManager.smartPermissions)
                            }
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Priscilla", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Allows Priscilla to access the internet and your location to answer questions about time, weather, news, and where you are.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = isSmartPriscillaTrulyEnabled,
                    onCheckedChange = null
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }

        item {
            Text("Model Management", style = MaterialTheme.typography.headlineMedium)
        }

        items(availableModels) { model ->
            val isSelected = selectedModel.fileName == model.fileName
            val modelFile = File(context.filesDir, model.fileName)
            val currentDownloadState = downloadState

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = {
                    if (modelFile.exists() && !isModelBusy) {
                        settingsViewModel.onModelSelected(model)
                    }
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(model.modelName, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))

                    if (currentDownloadState is DownloadState.Downloading && currentDownloadState.modelFileName == model.fileName) {
                        val progress = currentDownloadState.progress
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Downloading... $progress%", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                    } else if (currentDownloadState is DownloadState.Error && currentDownloadState.modelFileName == model.fileName) {
                        Text("Download failed: ${currentDownloadState.message}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { settingsViewModel.startModelDownload(model) }) {
                            Text("Retry Download")
                        }

                    } else {
                        val isDownloaded = model.downloadUrl == null || modelFile.exists()
                        if (isDownloaded) {
                            Button(
                                onClick = { settingsViewModel.onModelSelected(model) },
                                enabled = !isModelBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Selected")
                                } else {
                                    Text("Select this model")
                                }
                            }
                        } else {
                            Button(
                                onClick = { settingsViewModel.startModelDownload(model) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                                Spacer(Modifier.width(8.dp))
                                Text("Download Model")
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }

        item {
            Text("Model Parameters", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text("Temperature: ${String.format(Locale.US, "%.2f", temp)}", style = MaterialTheme.typography.bodyLarge)
            Text("Controls randomness. Lower is more predictable.", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = temp,
                onValueChange = { temp = it },
                valueRange = 0.0f..2.0f,
                enabled = !isModelBusy
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text("Top-P (Nucleus Sampling): ${String.format(Locale.US, "%.2f", topP)}", style = MaterialTheme.typography.bodyLarge)
            Text("Considers the smallest set of tokens whose cumulative probability exceeds P.", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = topP,
                onValueChange = { topP = it },
                valueRange = 0.0f..1.0f,
                enabled = !isModelBusy
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text("Repeat Penalty: ${String.format(Locale.US, "%.2f", repeatPenalty)}", style = MaterialTheme.typography.bodyLarge)
            Text("Penalizes repeating tokens. > 1.0 discourages repetition.", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = repeatPenalty,
                onValueChange = { repeatPenalty = it },
                valueRange = 1.0f..2.0f,
                enabled = !isModelBusy
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = topK,
                onValueChange = { if (it.all { char -> char.isDigit() }) topK = it },
                label = { Text("Top-K") },
                supportingText = { Text("Considers the top K most likely tokens. 0 to disable.") },
                enabled = !isModelBusy
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val newParams = parameters.copy(
                            temp = temp,
                            topK = topK.toIntOrNull() ?: SettingsRepository.DEFAULTS.topK,
                            topP = topP,
                            repeatPenalty = repeatPenalty
                        )
                        settingsViewModel.updateParameters(newParams)
                        settingsViewModel.triggerModelReload()
                        onReloadComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isModelBusy
            ) {
                Text("Apply & Reload Model")
            }

            if (isModelBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please wait for Priscilla to finish its current response before reloading.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}