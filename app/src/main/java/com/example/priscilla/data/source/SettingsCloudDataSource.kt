package com.example.priscilla.data.source

import android.util.Log
import com.example.priscilla.data.LlamaModel
import com.example.priscilla.data.ModelParameters
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.VoiceSettings
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

// A data class to represent the settings stored in Firestore
data class CloudSettings(
    val selectedModelFileName: String = LlamaModel.TinyLlama1B.fileName,
    val smartPriscillaEnabled: Boolean = false,
    val modelParameters: ModelParameters = SettingsRepository.DEFAULTS,
    val voiceSettings: VoiceSettings = VoiceSettings(),
    val lastModified: Long = System.currentTimeMillis(),
    val navItemOrder: List<String> = emptyList()
)

// The interface for our new cloud data source
interface SettingsCloudDataSource {
    suspend fun getSettings(userId: String): CloudSettings?
    suspend fun saveSettings(userId: String, settings: CloudSettings)
}

// The concrete implementation that uses Firebase Firestore
class SettingsCloudDataSourceImpl(
    private val firestore: FirebaseFirestore = Firebase.firestore
) : SettingsCloudDataSource {

    private companion object {
        const val USERS_COLLECTION = "users"
        const val SETTINGS_KEY = "user_settings"
    }

    override suspend fun getSettings(userId: String): CloudSettings? {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            // Settings are stored in a map within the main user document
            @Suppress("UNCHECKED_CAST")
            val settingsMap = document.get(SETTINGS_KEY) as? Map<String, Any> ?: return null

            val paramsMap = settingsMap["modelParameters"] as? Map<String, Any>
            val params = if (paramsMap != null) {
                ModelParameters(
                    temp = (paramsMap["temp"] as? Double)?.toFloat() ?: SettingsRepository.DEFAULTS.temp,
                    topK = (paramsMap["topK"] as? Long)?.toInt() ?: SettingsRepository.DEFAULTS.topK,
                    topP = (paramsMap["topP"] as? Double)?.toFloat() ?: SettingsRepository.DEFAULTS.topP,
                    repeatPenalty = (paramsMap["repeatPenalty"] as? Double)?.toFloat() ?: SettingsRepository.DEFAULTS.repeatPenalty
                )
            } else {
                SettingsRepository.DEFAULTS
            }

            @Suppress("UNCHECKED_CAST")
            val voiceMap = settingsMap["voiceSettings"] as? Map<String, Any>
            val voice = if (voiceMap != null) {
                VoiceSettings(
                    selectedVoiceId = voiceMap["selectedVoiceId"] as? String ?: "NONE",
                    pitch = (voiceMap["pitch"] as? Double)?.toFloat() ?: 1.0f,
                    speed = (voiceMap["speed"] as? Double)?.toFloat() ?: 1.0f
                )
            } else {
                VoiceSettings() // Default values
            }

            @Suppress("UNCHECKED_CAST")
            val navOrder = settingsMap["navItemOrder"] as? List<String> ?: emptyList()

            CloudSettings(
                selectedModelFileName = settingsMap["selectedModelFileName"] as? String ?: LlamaModel.TinyLlama1B.fileName,
                smartPriscillaEnabled = settingsMap["smartPriscillaEnabled"] as? Boolean ?: false,
                modelParameters = params,
                voiceSettings = voice,
                lastModified = settingsMap["lastModified"] as? Long ?: 0L,
                navItemOrder = navOrder
            )
        } catch (e: Exception) {
            Log.e("SettingsFirestore", "Error getting settings for user $userId", e)
            null
        }
    }

    override suspend fun saveSettings(userId: String, settings: CloudSettings) {
        try {
            val dataToSave = mapOf(SETTINGS_KEY to settings)
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .set(dataToSave, SetOptions.merge())
                .await()
            Log.i("SettingsFirestore", "Successfully saved settings for user $userId")
        } catch (e: Exception) {
            Log.e("SettingsFirestore", "Error saving settings for user $userId", e)
            throw e
        }
    }
}