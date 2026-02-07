/*
 * This file is part of Lux Alarm, authored by Daniel Salmun.
 *
 * Lux Alarm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lux Alarm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Lux Alarm.  If not, see <https://www.gnu.org/licenses/>.
 */
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val ALARM_CHANNEL_ID = "alarm_channel_id"
        const val ALARM_NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val alarmIds = intent?.getIntegerArrayListExtra("alarm_ids") ?: arrayListOf()
        val alarmId = alarmIds.firstOrNull() ?: -1

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alreadyRinging = AppContainer.repository.isAlarmRinging()

                if (!alreadyRinging) {
                    AppContainer.repository.setRingingAlarm()

                    val serviceIntent =
                        Intent(context, AlarmService::class.java).apply {
                            putExtra("alarm_id", alarmId)
                        }
                    context.startService(serviceIntent)

                    val activityIntent =
                        Intent(context, AlarmActivity::class.java).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            putExtra("alarm_id", alarmId)
                        }
                    context.startActivity(activityIntent)

                    createNotificationChannel(context)

                    val fullScreenIntent =
                        Intent(context, AlarmActivity::class.java).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("alarm_id", alarmId)
                        }
                    val fullScreenPendingIntent =
                        PendingIntent.getActivity(
                            context,
                            0,
                            fullScreenIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )

                    val notification =
                        NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
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
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            NotificationManagerCompat.from(context)
                                .notify(ALARM_NOTIFICATION_ID, notification)
                        }
                    } else {
                        NotificationManagerCompat.from(context)
                            .notify(ALARM_NOTIFICATION_ID, notification)
                    }
                }

                AppContainer.repository.deactivateOneShotAlarms(alarmIds.toList())
                AppContainer.repository.scheduleNextAlarm()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val name = "Alarm notifications"
        val descriptionText = "Notifications for triggered alarms"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel =
            NotificationChannel(ALARM_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setBypassDnd(true)
                enableVibration(true)
                setShowBadge(false)
            }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
