package com.dsalmun.luxalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Always allowed on older versions
        }
    }
    
    private fun scheduleExactAlarm(context: Context, triggerAtMillis: Long, requestCode: Int = 0, hour: Int? = null, minute: Int? = null): Boolean {
        if (!canScheduleExactAlarms(context)) {
            return false
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            hour?.let { putExtra("alarm_hour", it) }
            minute?.let { putExtra("alarm_minute", it) }
            putExtra("alarm_id", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        return true
    }

    fun scheduleExactAlarmAt(context: Context, hour: Int, minute: Int, requestCode: Int, repeatDays: Set<Int>): Boolean {
        val triggerAtMillis = if (repeatDays.isEmpty()) {
            val now = Calendar.getInstance()
            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1) // schedule for next day if time has passed
                }
            }
            alarmTime.timeInMillis
        } else {
            calculateNextTrigger(hour, minute, repeatDays)
        }

        if (triggerAtMillis == -1L || !canScheduleExactAlarms(context)) {
            return false
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_hour", hour)
            putExtra("alarm_minute", minute)
            putExtra("alarm_id", requestCode)
            putIntegerArrayListExtra("repeat_days", ArrayList(repeatDays))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        return true
    }

    private fun calculateNextTrigger(hour: Int, minute: Int, repeatDays: Set<Int>): Long {
        val now = Calendar.getInstance()
        for (i in 0..7) {
            val checkDay = (now.get(Calendar.DAY_OF_WEEK) - 1 + i) % 7 + 1
            if (checkDay in repeatDays) {
                val potentialTrigger = now.clone() as Calendar
                potentialTrigger.add(Calendar.DAY_OF_YEAR, i)
                potentialTrigger.set(Calendar.HOUR_OF_DAY, hour)
                potentialTrigger.set(Calendar.MINUTE, minute)
                potentialTrigger.set(Calendar.SECOND, 0)
                potentialTrigger.set(Calendar.MILLISECOND, 0)

                if (potentialTrigger.after(now)) {
                    return potentialTrigger.timeInMillis
                }
            }
        }
        return -1L
    }

    fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
} 