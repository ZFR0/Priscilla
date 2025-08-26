package com.example.priscilla.data.source

import android.util.Log
import com.example.priscilla.data.ReminderEntity
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

// The interface for our new cloud data source
interface RemindersCloudDataSource {
    suspend fun getReminders(userId: String): List<ReminderEntity>
    suspend fun uploadReminders(userId: String, reminders: List<ReminderEntity>)
}

// The concrete implementation that uses Firebase Firestore
class RemindersCloudDataSourceImpl(
    private val firestore: FirebaseFirestore = Firebase.firestore
) : RemindersCloudDataSource {

    private companion object {
        const val USERS_COLLECTION = "users"
        const val REMINDERS_COLLECTION = "reminders"
    }

    override suspend fun getReminders(userId: String): List<ReminderEntity> {
        return try {
            Log.d("SYNC_DEBUG", "RemindersCloudDataSource: Fetching from Firestore for user $userId...")
            Log.d("ReminderSync", "Fetching reminders from Firestore for user $userId...")
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(REMINDERS_COLLECTION)
                .get()
                .await()
            Log.d("SYNC_DEBUG", "RemindersCloudDataSource: Firestore returned ${snapshot.size()} reminders.")
            Log.d("ReminderSync", "Successfully fetched ${snapshot.size()} reminders from Firestore.")

            // Convert the Firestore documents back into our ReminderEntity data class
            snapshot.documents.mapNotNull { doc ->
                ReminderEntity(
                    // Read the ID as a String ---
                    id = doc.getString("id") ?: doc.id,
                    task = doc.getString("task") ?: "",
                    triggerAtMillis = (doc.get("triggerAtMillis") as? Number)?.toLong() ?: 0L,
                    // Read the new timestamp ---
                    lastModified = (doc.get("lastModified") as? Number)?.toLong() ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "RemindersCloudDataSource: Error getting reminders for user $userId", e)
            Log.e("ReminderSync", "Error getting reminders for user $userId", e)
            emptyList()
        }
    }

    override suspend fun uploadReminders(userId: String, reminders: List<ReminderEntity>) {
        try {
            Log.d("ReminderSync", "Uploading ${reminders.size} reminders to Firestore...")
            val batch = firestore.batch()

            reminders.forEach { reminder ->
                // Create a reference using the String UUID as the document ID
                val docRef = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(REMINDERS_COLLECTION)
                    // The ID is now a String, no .toString() needed ---
                    .document(reminder.id)

                // The data map is identical to the ReminderEntity
                batch.set(docRef, reminder)
            }

            // Commit all reminders in a single operation
            batch.commit().await()
            Log.i("ReminderSync", "Successfully uploaded ${reminders.size} reminders.")
        } catch (e: Exception) {
            Log.e("ReminderSync", "Error uploading reminders for user $userId", e)
            throw e
        }
    }
}