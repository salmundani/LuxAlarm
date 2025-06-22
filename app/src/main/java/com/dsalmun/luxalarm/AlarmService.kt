package com.dsalmun.luxalarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.core.content.edit

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.dsalmun.luxalarm.STOP_ALARM"
        const val ACTION_DISABLE_ALARM = "com.dsalmun.luxalarm.DISABLE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val ALARM_PLAYING_PREF = "alarm_playing"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarm()
                START_NOT_STICKY
            }
            else -> {
                startAlarm()
                START_STICKY
            }
        }
    }

    private fun startAlarm() {
        try {
            // Start playing alarm sound
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
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
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
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

        // Send broadcast to disable all alarms that were playing
        playingAlarmIds.forEach { alarmIdString ->
            val alarmIdInt = alarmIdString.toIntOrNull()
            if (alarmIdInt != null) {
                android.util.Log.d("AlarmService", "Broadcasting disable for alarm ID: $alarmIdInt")
                val disableIntent = Intent(ACTION_DISABLE_ALARM).apply {
                    putExtra(EXTRA_ALARM_ID, alarmIdInt)
                    setPackage(packageName)
                }
                sendBroadcast(disableIntent)
            }
        }

        stopSelf()
    }

    private fun dismissNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.ALARM_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
} 