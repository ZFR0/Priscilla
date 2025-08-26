package com.example.priscilla.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// Define a separate DataStore for sync-related state
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

// The interface for our new repository
interface SyncStateRepository {
    // Gets the locally saved timestamp as a Flow
    fun getLocalLastSyncTimestampFlow(): Flow<Long>

    // Gets the timestamp currently stored in the cloud (one-time fetch)
    suspend fun getCloudLastSyncTimestamp(userId: String): Long

    // Updates both the local and cloud timestamps after a successful sync
    suspend fun updateLastSyncTimestamp(userId: String, timestamp: Long)
}


class SyncStateRepositoryImpl(
    private val context: Context,
    private val firestore: FirebaseFirestore
) : SyncStateRepository {

    // Define the key for storing the timestamp in DataStore
    private companion object {
        val LAST_SYNC_TIMESTAMP_KEY = longPreferencesKey("last_successful_sync")
        const val USERS_COLLECTION = "users"
        const val LAST_SYNCED_FIELD = "lastSynced"
    }

    override fun getLocalLastSyncTimestampFlow(): Flow<Long> {
        return context.syncDataStore.data.map { preferences ->
            // Return the saved value, or 0L if it has never been set
            preferences[LAST_SYNC_TIMESTAMP_KEY] ?: 0L
        }
    }

    override suspend fun getCloudLastSyncTimestamp(userId: String): Long {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            document.getLong(LAST_SYNCED_FIELD) ?: 0L
        } catch (e: Exception) {
            Log.e("SyncStateRepository", "Failed to fetch cloud timestamp for user $userId", e)
            0L // Return 0 on failure
        }
    }

    override suspend fun updateLastSyncTimestamp(userId: String, timestamp: Long) {
        try {
            // Update the cloud value
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(LAST_SYNCED_FIELD, timestamp)
                .await()

            // If cloud update is successful, update the local value
            context.syncDataStore.edit { preferences ->
                preferences[LAST_SYNC_TIMESTAMP_KEY] = timestamp
            }
            Log.i("SyncStateRepository", "Successfully updated last sync timestamp to $timestamp")
        } catch (e: Exception) {
            Log.e("SyncStateRepository", "Failed to update last sync timestamp", e)
        }
    }
}