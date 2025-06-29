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

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AlarmService = this@AlarmService
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.dsalmun.luxalarm.STOP_ALARM"
        private const val ALARM_PLAYING_PREF = "alarm_playing"
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarm()
                START_NOT_STICKY
            }
            else -> {
                startAlarm()
                START_STICKY
            }
        }

    private fun startAlarm() {
        try {
            // Start playing alarm sound
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }

            // Start vibration
            vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }

            val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        dismissNotification()

        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        val sharedPrefs = getSharedPreferences("luxalarm_prefs", MODE_PRIVATE)

        val playingAlarmIds = sharedPrefs.getStringSet("alarm_ids", emptySet()) ?: emptySet()

        sharedPrefs.edit {
            putBoolean(ALARM_PLAYING_PREF, false)
                .putStringSet("alarm_ids", emptySet()) // Clear the playing alarms
        }

        // Reschedule all alarms that were playing
        playingAlarmIds.forEach { alarmIdString ->
            val alarmIdInt = alarmIdString.toIntOrNull()
            if (alarmIdInt != null) {
                serviceScope.launch {
                    AppContainer.repository.rescheduleAlarmAfterPlaying(alarmIdInt)
                }
            }
        }

        stopSelf()
    }

    private fun dismissNotification() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.ALARM_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
