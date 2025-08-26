// File: SettingsRepository.kt

package com.example.priscilla.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.priscilla.AuthRepository
import com.example.priscilla.SyncResult
import com.example.priscilla.UserScopeCleanup
import com.example.priscilla.data.source.SettingsCloudDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.priscilla.data.source.CloudSettings


data class VoiceSettings(
    val selectedVoiceId: String = "NONE", // "NONE" will mean TTS is disabled
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f
)

// Define the DataStore instance at the top level
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// We define the available models here. This is the single source of truth for model info.
sealed class LlamaModel(val modelName: String, val fileName: String, val downloadUrl: String?) {
    object TinyLlama1B : LlamaModel(
        modelName = "TinyLlama 1B (Fast, Bundled)",
        fileName = "priscilla-q4_K_Mv3.gguf",
        downloadUrl = null // null means it's in the app assets
    )
    object Qwen4B : LlamaModel(
        modelName = "Qwen 1.5 4B (Higher Quality)",
        fileName = "priscilla-qwen-q4_K_M.gguf",
        // Replace this with your real Hugging Face URL
        downloadUrl = "https://huggingface.co/ZFR0/priscilla-gguf-models/resolve/main/priscilla-qwen-q4_K_M.gguf"
    )
}

// A list of all models available for the UI to display
val availableModels: List<LlamaModel> = listOf(LlamaModel.TinyLlama1B, LlamaModel.Qwen4B)

data class ModelParameters(
    val temp: Float,
    val topK: Int,
    val topP: Float,
    val repeatPenalty: Float
)

class SettingsRepository(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val settingsCloudDataSource: SettingsCloudDataSource,
    private val permissionManager: PermissionManager
) : UserScopeCleanup {

    private val dataStore = context.dataStore

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSyncing = false // A simple flag to prevent sync loops

    init {
        repositoryScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user != null && !user.isGuest) {
                    // When a user logs in, trigger a sync from cloud to local.
                    syncCloudToLocal(user.uid)
                }
            }
        }
    }

    override fun onSignOut() {
        Log.d("SettingsRepository", "Sign out detected. Resetting voice settings to default.")
        // We need to run this in a coroutine scope since `updateVoiceSettings` is a suspend function.
        repositoryScope.launch {
            // Reset to default by creating a new, empty VoiceSettings object.
            updateVoiceSettings(VoiceSettings())
        }
    }

    private suspend fun syncCloudToLocal(userId: String) {
        if (isSyncing) return
        isSyncing = true

        val cloudSettings = settingsCloudDataSource.getSettings(userId)
        if (cloudSettings != null) {
            // Sync all non-permission-related settings first
            updateSelectedModel(availableModels.find { it.fileName == cloudSettings.selectedModelFileName } ?: LlamaModel.TinyLlama1B)
            updateParameters(cloudSettings.modelParameters)
            updateVoiceSettings(cloudSettings.voiceSettings)

            // Sync the nav order if it exists in the cloud
            if (cloudSettings.navItemOrder.isNotEmpty()) {
                updateNavItemOrder(cloudSettings.navItemOrder)
            }

            val cloudPreference = cloudSettings.smartPriscillaEnabled
            val finalPreference: Boolean

            if (cloudPreference && !permissionManager.areAllSmartPermissionsGranted()) {
                // The user's preference is ON in the cloud, but this device lacks permissions.
                // We must disable the preference locally to prevent a broken state.
                Log.w("SettingsRepository", "Cloud preference for Smart Priscilla is ON, but permissions are missing. Forcing OFF locally.")
                finalPreference = false
            } else {
                // The preference is OFF, or it's ON and we have permissions. Apply as is.
                finalPreference = cloudPreference
            }
            updateSmartPriscillaEnabled(finalPreference)
            Log.i("SettingsRepository", "Successfully synced settings from cloud to local.")
        }
        isSyncing = false
    }

    suspend fun migrateCloudToLocal(userId: String) {
        Log.i("SettingsRepository", "Starting FULL Cloud -> Local sync for settings.")
        // Clear local settings.
        clearLocalSettings()

        // Manually fetch from the cloud. We can reuse the existing sync function's logic.
        // We call the existing `syncCloudToLocal` which already does the fetch and update.
        syncCloudToLocal(userId)
        Log.i("SettingsRepository", "Full migration for settings complete.")
    }

    private suspend fun getLocalSettingsAsCloudObject(): CloudSettings {
        val selectedModel = selectedModelFlow.first()
        val smartEnabled = smartPriscillaEnabledFlow.first()
        val params = modelParametersFlow.first()
        val voice = voiceSettingsFlow.first()
        val navOrder = navItemOrderFlow.first()

        return CloudSettings(
            selectedModelFileName = selectedModel.fileName,
            smartPriscillaEnabled = smartEnabled,
            modelParameters = params,
            voiceSettings = voice,
            lastModified = System.currentTimeMillis(),
            navItemOrder = navOrder
        )
    }

    suspend fun clearLocalSettings() {
        Log.i("SettingsRepository", "Clearing all local settings from DataStore.")
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun uploadUnsyncedSettings(userId: String, lastSyncTimestamp: Long): SyncResult {
        // Correctly return a SyncResult if already syncing
        if (isSyncing) return SyncResult(uploaded = 0, success = true)
        isSyncing = true

        return try {
            val localTimestamp = context.dataStore.data.first()[LAST_MODIFIED_TIMESTAMP_KEY] ?: 0L
            if (localTimestamp > lastSyncTimestamp) {
                Log.i("SettingsRepository", "Found unsynced settings. Uploading...")
                val localSettings = getLocalSettingsAsCloudObject()
                settingsCloudDataSource.saveSettings(userId, localSettings)
                // The return statement is the last expression
                SyncResult(uploaded = 1)
            } else {
                Log.i("SettingsRepository", "Settings are already synced. Nothing to upload.")
                // The return statement is the last expression
                SyncResult(uploaded = 0)
            }
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Upload of settings FAILED", e)
            // The return statement is the last expression
            SyncResult(success = false)
        } finally {
            isSyncing = false
        }
    }

    suspend fun hasUnsyncedData(lastSyncTimestamp: Long): Boolean {
        // We'll read the timestamp stored with our local settings object.
        // We need to add this timestamp to DataStore first.
        val localTimestamp = context.dataStore.data.first()[LAST_MODIFIED_TIMESTAMP_KEY] ?: 0L
        return localTimestamp > lastSyncTimestamp
    }

    val navItemOrderFlow: Flow<List<String>> = dataStore.data
        .map { preferences ->
            val defaultOrder = "chat,history,reminders,settings,profile"
            // Read the saved string, split it by comma, or fall back to default
            val savedOrder = preferences[KEY_NAV_ITEM_ORDER]
            if (savedOrder.isNullOrBlank()) {
                defaultOrder.split(',')
            } else {
                savedOrder.split(',')
            }
        }

    suspend fun updateNavItemOrder(newOrder: List<String>) {
        dataStore.edit { settings ->
            settings[KEY_NAV_ITEM_ORDER] = newOrder.joinToString(",")
            // Also update the master timestamp for cloud sync
            settings[LAST_MODIFIED_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    companion object {
        val KEY_TEMP = floatPreferencesKey("model_temp")
        val KEY_TOP_K = intPreferencesKey("model_top_k")
        val KEY_TOP_P = floatPreferencesKey("model_top_p")
        val KEY_REPEAT_PENALTY = floatPreferencesKey("model_repeat_penalty")
        val KEY_SMART_PRISCILLA_ENABLED = booleanPreferencesKey("smart_priscilla_enabled")
        val LAST_MODIFIED_TIMESTAMP_KEY = longPreferencesKey("settings_last_modified")
        val KEY_SELECTED_MODEL_FILENAME = stringPreferencesKey("selected_model_filename")
        val KEY_NAV_ITEM_ORDER = stringPreferencesKey("nav_item_order")

        val KEY_VOICE_ID = stringPreferencesKey("voice_id")
        val KEY_VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val KEY_VOICE_SPEED = floatPreferencesKey("voice_speed")

        val DEFAULTS = ModelParameters(
            temp = 0.7f,
            topK = 50,
            topP = 0.95f,
            repeatPenalty = 1.15f
        )
    }

    val selectedModelFlow: Flow<LlamaModel> = dataStore.data
        .map { preferences ->
            val fileName = preferences[KEY_SELECTED_MODEL_FILENAME] ?: LlamaModel.TinyLlama1B.fileName
            // Find the model in our list that matches the saved filename, or fall back to TinyLlama
            availableModels.find { it.fileName == fileName } ?: LlamaModel.TinyLlama1B
        }

    suspend fun updateSelectedModel(model: LlamaModel) {
        dataStore.edit { settings ->
            settings[KEY_SELECTED_MODEL_FILENAME] = model.fileName
            settings[LAST_MODIFIED_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    val smartPriscillaEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[KEY_SMART_PRISCILLA_ENABLED] ?: false
        }

    suspend fun updateSmartPriscillaEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SMART_PRISCILLA_ENABLED] = isEnabled
            preferences[LAST_MODIFIED_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    val modelParametersFlow: Flow<ModelParameters> = dataStore.data
        .map { preferences ->
            ModelParameters(
                temp = preferences[KEY_TEMP] ?: DEFAULTS.temp,
                topK = preferences[KEY_TOP_K] ?: DEFAULTS.topK,
                topP = preferences[KEY_TOP_P] ?: DEFAULTS.topP,
                repeatPenalty = preferences[KEY_REPEAT_PENALTY] ?: DEFAULTS.repeatPenalty
            )
        }

    val voiceSettingsFlow: Flow<VoiceSettings> = dataStore.data
        .map { preferences ->
            VoiceSettings(
                selectedVoiceId = preferences[KEY_VOICE_ID] ?: "NONE",
                pitch = preferences[KEY_VOICE_PITCH] ?: 1.0f,
                speed = preferences[KEY_VOICE_SPEED] ?: 1.0f
            )
        }

    suspend fun updateVoiceSettings(settings: VoiceSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_VOICE_ID] = settings.selectedVoiceId
            preferences[KEY_VOICE_PITCH] = settings.pitch
            preferences[KEY_VOICE_SPEED] = settings.speed
            // Always update the master timestamp when any setting changes
            preferences[LAST_MODIFIED_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun updateParameters(params: ModelParameters) {
        dataStore.edit { preferences ->
            preferences[KEY_TEMP] = params.temp
            preferences[KEY_TOP_K] = params.topK
            preferences[KEY_TOP_P] = params.topP
            preferences[KEY_REPEAT_PENALTY] = params.repeatPenalty
            preferences[LAST_MODIFIED_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun getSelectedModel(): LlamaModel {
        return selectedModelFlow.first()
    }
}