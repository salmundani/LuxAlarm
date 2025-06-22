package com.dsalmun.luxalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val ALARM_PLAYING_PREF = "alarm_playing"
        private const val ALARM_HOUR_PREF = "alarm_hour"
        private const val ALARM_MINUTE_PREF = "alarm_minute"
        private const val ALARM_IDS_PREF = "alarm_ids"
    }
    
    override fun onReceive(context: Context, intent: Intent?) {
        val sharedPrefs = context.getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
        
        val alarmHour = intent?.getIntExtra("alarm_hour", -1) ?: -1
        val alarmMinute = intent?.getIntExtra("alarm_minute", -1) ?: -1
        val alarmId = intent?.getIntExtra("alarm_id", -1) ?: -1
        
        // Check if this is the first alarm for this time
        val isFirstAlarmForThisTime = !sharedPrefs.getBoolean(ALARM_PLAYING_PREF, false) ||
                                      sharedPrefs.getInt(ALARM_HOUR_PREF, -1) != alarmHour ||
                                      sharedPrefs.getInt(ALARM_MINUTE_PREF, -1) != alarmMinute
        
        // Collect alarm IDs for the current time
        val currentAlarmIds = if (isFirstAlarmForThisTime) {
            // Start fresh for a new time
            mutableSetOf(alarmId.toString())
        } else {
            // Add to existing alarms for the same time
            val existingIds = sharedPrefs.getStringSet(ALARM_IDS_PREF, emptySet())?.toMutableSet() ?: mutableSetOf()
            existingIds.add(alarmId.toString())
            existingIds
        }
        
        sharedPrefs.edit {
            putBoolean(ALARM_PLAYING_PREF, true)
                .putInt(ALARM_HOUR_PREF, alarmHour)
                .putInt(ALARM_MINUTE_PREF, alarmMinute)
                .putStringSet(ALARM_IDS_PREF, currentAlarmIds)
        }
        
        // Only start the service and activity for the first alarm at this time
        if (isFirstAlarmForThisTime) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra("alarm_id", alarmId)
            }
            context.startService(serviceIntent)
            
            val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                       Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra("alarm_id", alarmId)
            }
            context.startActivity(activityIntent)
            
            val channelId = "alarm_channel_id"
            createNotificationChannel(context, channelId)

            // Create a pending intent for the full screen intent
            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("alarm_id", alarmId)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alarm Ringing")
                .setContentText("Tap to open alarm screen")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
                }
            } else {
                // For older versions, permission is granted by default
                NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            }
        }
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        val name = "Alarm notifications"
        val descriptionText = "Notifications for triggered alarms"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            setBypassDnd(true)
            enableVibration(true)
            setShowBadge(false)
        }
        // Register the channel with the system
        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
} 