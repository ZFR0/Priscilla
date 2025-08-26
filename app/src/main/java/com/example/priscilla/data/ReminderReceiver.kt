package com.example.priscilla.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.priscilla.MainActivity
import com.example.priscilla.R
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint

class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("ObsoleteSdkInt")
    override fun onReceive(context: Context, intent: Intent) {
        val reminderIdString = intent.getStringExtra("REMINDER_ID_STRING")
        val task = intent.getStringExtra("REMINDER_TASK") ?: "You have a reminder."

        if (reminderIdString.isNullOrBlank()) return

        val notificationId = reminderIdString.hashCode()
        // --- Add a permission check for Android 13+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("ReminderReceiver", "POST_NOTIFICATIONS permission not granted. Cannot show reminder.")
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "priscilla_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Priscilla Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for reminders set by Priscilla."
            }
            notificationManager.createNotificationChannel(channel)
        }
        // Create an Intent to open the app when the notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the Notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setContentTitle("A Reminder from Priscilla")
            .setContentText(task)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}