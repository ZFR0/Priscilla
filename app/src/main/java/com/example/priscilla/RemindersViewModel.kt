package com.example.priscilla

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.priscilla.data.ReminderEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PartitionedReminders(
    val pending: List<ReminderEntity> = emptyList(),
    val expired: List<ReminderEntity> = emptyList()
)

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderRepository = (application as PriscillaApplication).appContainer.reminderRepository

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    private var refreshJob: Job? = null

    // The partition logic now combines the DB flow with our refresh trigger ---
    private val partitionedReminders: StateFlow<PartitionedReminders> = reminderRepository.allReminders
        .combine(refreshTrigger) { reminders, _ -> // We only care that the trigger emitted
            val now = System.currentTimeMillis()
            val (pending, expired) = reminders.partition { it.triggerAtMillis > now }

            // Sort pending reminders to easily find the next one
            val sortedPending = pending.sortedBy { it.triggerAtMillis }

            PartitionedReminders(pending = sortedPending, expired = expired)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PartitionedReminders()
        )

    val pendingReminders: StateFlow<List<ReminderEntity>> = partitionedReminders
        .map { it.pending }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val expiredReminders: StateFlow<List<ReminderEntity>> = partitionedReminders
        .map { it.expired }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            // We listen to the result of our partitioning (the pending list)
            pendingReminders.collectLatest { pendingList ->
                // Cancel any previously scheduled refresh
                refreshJob?.cancel()

                // Find the very next reminder that will expire
                val nextReminder = pendingList.firstOrNull()

                if (nextReminder != null) {
                    // If we found one, schedule a precise refresh
                    scheduleNextRefresh(nextReminder.triggerAtMillis)
                } else {
                    Log.d("RemindersViewModel", "No pending reminders, stopping precise timer.")
                }
            }
        }
    }

    private fun scheduleNextRefresh(triggerAtMillis: Long) {
        val now = System.currentTimeMillis()
        val delayMillis = triggerAtMillis - now

        if (delayMillis > 0) {
            refreshJob = viewModelScope.launch {
                Log.d("RemindersViewModel", "Next reminder expires in $delayMillis ms. Scheduling refresh.")
                delay(delayMillis)
                Log.d("RemindersViewModel", "Timer finished. Triggering UI refresh.")
                // When the delay is over, emit a new value to our trigger
                refreshTrigger.value = System.currentTimeMillis()
            }
        } else {
            // If the time is already in the past, refresh immediately
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.delete(reminder)
            // Note: Deleting a reminder will cause the `allReminders` flow to emit a new
            // list, which will automatically re-trigger our `collectLatest` in the init
            // block and reschedule the timer for the *new* next reminder.
        }
    }
}