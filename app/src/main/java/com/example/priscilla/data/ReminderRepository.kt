package com.example.priscilla.data

import kotlinx.coroutines.flow.Flow
import android.util.Log
import com.example.priscilla.AuthRepository
import com.example.priscilla.SyncResult
import com.example.priscilla.UserScopeCleanup
import com.example.priscilla.data.source.RemindersCloudDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * A repository to act as the single source of truth for reminder data.
 * This class abstracts the data source (the DAO) from the ViewModels and Executors.
 * It ensures all parts of the app are observing the same, live data stream.
 */
class ReminderRepository(
    private val context: Context,
    private val chatDao: ChatDao,
    private val authRepository: AuthRepository,
    private val remindersCloudDataSource: RemindersCloudDataSource
) : UserScopeCleanup {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * A public Flow that exposes the live list of all reminders from the database.
     * Any UI component observing this flow will automatically update when a reminder
     * is added or removed.
     */
    val allReminders: Flow<List<ReminderEntity>> = chatDao.getAllReminders()

    init {
        repositoryScope.launch {
            // Use collectLatest for better lifecycle handling ---
            authRepository.currentUser.collectLatest { user ->
                if (user != null && !user.isGuest) {
                    syncCloudToLocal(user.uid)
                }
            }
        }
    }

    suspend fun hasUnsyncedData(lastSyncTimestamp: Long): Boolean {
        val localReminders = allReminders.first()
        return localReminders.any { it.lastModified > lastSyncTimestamp }
    }

    suspend fun uploadUnsyncedReminders(userId: String, lastSyncTimestamp: Long): SyncResult {
        return try {
            val unsynced = allReminders.first().filter { it.lastModified > lastSyncTimestamp }
            if (unsynced.isNotEmpty()) {
                remindersCloudDataSource.uploadReminders(userId, unsynced)
                SyncResult(uploaded = unsynced.size) // Return result with count
            } else {
                SyncResult(uploaded = 0) // Return result with 0
            }
        } catch (e: Exception) {
            SyncResult(success = false) // Return failure result
        }
    }

    override fun onSignOut() {
        // No real-time listeners to cancel for reminders, so this is empty.
    }

    private suspend fun syncCloudToLocal(userId: String) {
        val cloudReminders = remindersCloudDataSource.getReminders(userId)

        // More robust "upsert" logic
        // This is a simple cloud-wins strategy
        if (cloudReminders.isNotEmpty()) {
            Log.i("ReminderSync", "Found ${cloudReminders.size} reminders in cloud. Syncing to local DB.")
            chatDao.insertReminders(cloudReminders)
        } else {
            Log.d("ReminderSync", "No reminders found in cloud for user $userId.")
        }
    }

    suspend fun migrateCloudToLocal(userId: String) {
        Log.i("SYNC_DEBUG", "ReminderRepository: migrateCloudToLocal() started.")
        Log.i("ReminderRepository", "Starting FULL Cloud -> Local sync for reminders.")
        // Step 1: Clear all existing local reminders.
        clearLocalReminders()

        // Step 2: Manually perform a ONE-TIME fetch from the cloud.
        try {
            Log.d("SYNC_DEBUG", "ReminderRepository: Fetching reminders from cloud...")
            Log.d("ReminderRepository", "Fetching all reminders from cloud for migration...")
            val cloudReminders = remindersCloudDataSource.getReminders(userId)
            if (cloudReminders.isNotEmpty()) {
                Log.i("SYNC_DEBUG", "ReminderRepository: Successfully migrated ${cloudReminders.size} reminders.")
                // Step 3: Insert the fetched reminders into our now-empty local DB.
                chatDao.insertReminders(cloudReminders)
                Log.i("ReminderRepository", "Successfully migrated ${cloudReminders.size} reminders.")
            } else {
                Log.d("SYNC_DEBUG", "ReminderRepository: No cloud reminders found to migrate.")
            }
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "ReminderRepository: FAILED during cloud migration.", e)
            Log.e("ReminderRepository", "Failed during manual cloud to local migration for reminders.", e)
        }
    }

    suspend fun clearLocalReminders() {
        try {
            val remindersBefore = allReminders.first()
            Log.d("SYNC_DEBUG", "ReminderRepository: clearLocalReminders() called. Found ${remindersBefore.size} reminders to delete.")

            chatDao.clearReminders()

            val remindersAfter = allReminders.first()
            Log.d("SYNC_DEBUG", "ReminderRepository: chatDao.clearReminders() finished. There are now ${remindersAfter.size} reminders locally.")
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "ReminderRepository: Exception in clearLocalReminders()", e)
        }
    }


    /**
     * A suspend function to insert a new reminder into the database.
     * The ID is now a String UUID, so we return that.
     */
    // The function signature and return type ---
    suspend fun insert(reminder: ReminderEntity): String {
        val newReminder = reminder.copy(
            id = reminder.id, // Keep the generated UUID
            lastModified = System.currentTimeMillis()
        )
        chatDao.insertReminder(newReminder)
        return newReminder.id // Return the String ID
    }

    /**
     * Deletes a reminder from the local database and cancels its pending system alarm.
     */
    suspend fun delete(reminder: ReminderEntity) {
        // Step 1: Cancel the pending system alarm.
        // We must re-create the *exact* same PendingIntent that was used to set the alarm.
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // We only need enough data to make the Intent unique. The task is good for this.
            putExtra("REMINDER_TASK", reminder.task)
            putExtra("REMINDER_ID_STRING", reminder.id)
        }

        val requestCode = reminder.id.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d("ReminderRepository", "Canceled system alarm for reminder ID: ${reminder.id}")

        // Step 2: Delete the reminder from the Room database.
        chatDao.deleteReminderById(reminder.id)
        Log.d("ReminderRepository", "Deleted reminder from database ID: ${reminder.id}")
    }
}