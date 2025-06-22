package com.dsalmun.luxalarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.dsalmun.luxalarm.STOP_ALARM"
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
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        val sharedPrefs = getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(ALARM_PLAYING_PREF, false)
            .apply()

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
} 