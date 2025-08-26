package com.example.priscilla.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// A new data class to provide a clean confirmation message
data class ActionResult(val confirmationMessage: String)

/**
 * This class is responsible for executing actions on the Android system,
 * such as setting alarms or reminders.
 */
class ActionExecutor(private val context: Context,
                     private val reminderRepository: ReminderRepository
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Takes a ReminderInfo object from the parser, calculates the trigger time,
     * saves it to the database, and schedules a system alarm.
     */
    suspend fun setReminder(reminderInfo: ReminderInfo?): ActionResult {
        if (reminderInfo == null) {
            return ActionResult("Your reminder is unclear. Please state the task and the time.")
        }

        val triggerAtMillis: Long = try {
            when (val parsedTime = reminderInfo.parsedTime) {
                is ParsedTime.Relative -> {
                    // CASE 1: Handle relative times like "in 5 minutes" or "next week".
                    val calendar = Calendar.getInstance()
                    val timeContext = parsedTime.context

                    // The tense is now handled more robustly by the parser itself.
                    when (timeContext.unit) {
                        TimeUnit.SECOND -> calendar.add(Calendar.SECOND, timeContext.value)
                        TimeUnit.MINUTE -> calendar.add(Calendar.MINUTE, timeContext.value)
                        TimeUnit.HOUR -> calendar.add(Calendar.HOUR, timeContext.value)
                        TimeUnit.DAY -> calendar.add(Calendar.DAY_OF_YEAR, timeContext.value)
                        TimeUnit.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, timeContext.value)
                        TimeUnit.MONTH -> calendar.add(Calendar.MONTH, timeContext.value)
                        TimeUnit.YEAR -> calendar.add(Calendar.YEAR, timeContext.value)
                    }
                    calendar.timeInMillis
                }
                is ParsedTime.Absolute -> {
                    // CASE 2: Handle absolute times like "tonight" or "this evening".
                    val calendar = Calendar.getInstance()
                    val absolute = parsedTime.time

                    // We directly set the calendar from the pre-calculated absolute time.
                    calendar.set(Calendar.YEAR, absolute.year)
                    calendar.set(Calendar.MONTH, absolute.month - 1) // Calendar month is 0-11
                    calendar.set(Calendar.DAY_OF_MONTH, absolute.dayOfMonth)
                    calendar.set(Calendar.HOUR_OF_DAY, absolute.hourOfDay)
                    calendar.set(Calendar.MINUTE, absolute.minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    calendar.timeInMillis
                }
                // This else branch will never be reached as long as ParsedTime is a sealed class
                // with only two subtypes. However, it's required by the compiler for exhaustiveness.
                // We throw an exception because this represents an impossible state.
                else -> throw IllegalStateException("Unknown ParsedTime type")
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error calculating trigger time", e)
            return ActionResult("The time you specified for the reminder is invalid.")
        }

        val reminderEntity = ReminderEntity(
            task = reminderInfo.task,
            triggerAtMillis = triggerAtMillis
        )
        // Insert the reminder. The function now returns the String UUID.
        val reminderIdString = reminderRepository.insert(reminderEntity)

        // For the PendingIntent's request code, we need an Integer.
        // A UUID's hashCode is a stable and suitable integer for this purpose.
        val requestCode = reminderIdString.hashCode()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // We can still pass the ID if needed, but it's now a string.
            putExtra("REMINDER_ID_STRING", reminderIdString)
            putExtra("REMINDER_TASK", reminderInfo.task)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode, // Use the hash code as the unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e("ActionExecutor", "Missing SCHEDULE_EXACT_ALARM permission", e)
            return ActionResult("I lack the permission to set precise reminders on this device.")
        }

        val formattedTime = SimpleDateFormat("h:mm a 'on' EEE, MMM d", Locale.getDefault()).format(triggerAtMillis)
        return ActionResult("A reminder to '${reminderInfo.task}' for $formattedTime has been set.")
    }
}