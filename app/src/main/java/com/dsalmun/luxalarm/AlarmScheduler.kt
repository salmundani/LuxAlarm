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
        val triggerAtMillis = calculateNextTrigger(hour, minute, repeatDays)

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
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            if (alarmTime.before(now)) {
                alarmTime.add(Calendar.DAY_OF_MONTH, 1) // schedule for next day if time has passed
            }
            return alarmTime.timeInMillis
        }

        // Find the next valid trigger time
        for (i in 0 until 7) {
            val potentialNextDay = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, i)
            }
            val dayOfWeek = potentialNextDay.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek in repeatDays) {
                val triggerTime = Calendar.getInstance().apply {
                    time = potentialNextDay.time
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (triggerTime.after(now)) {
                    return triggerTime.timeInMillis
                }
            }
        }

        // If no time was found, it means the next alarm is next week. Find the first day of the week.
        var firstDayOfWeek = 8
        for (day in repeatDays) {
            if (day < firstDayOfWeek) {
                firstDayOfWeek = day
            }
        }

        val nextWeekAlarm = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return nextWeekAlarm.timeInMillis
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