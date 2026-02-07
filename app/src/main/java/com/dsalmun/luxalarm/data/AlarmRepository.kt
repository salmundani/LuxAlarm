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
package com.dsalmun.luxalarm.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dsalmun.luxalarm.AlarmReceiver
import com.dsalmun.luxalarm.BootReceiver
import com.dsalmun.luxalarm.MainActivity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val context: Context,
) : IAlarmRepository {
    private companion object {
        const val NEXT_ALARM_REQUEST_CODE = 0
        const val PREFS_NAME = "alarm_state"
        const val KEY_IS_RINGING = "is_ringing"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun getAllAlarms(): Flow<List<AlarmItem>> = alarmDao.getAllAlarms()

    override suspend fun addAlarm(hour: Int, minute: Int): Boolean {
        val newAlarm = AlarmItem(hour = hour, minute = minute)
        val newId = alarmDao.insert(newAlarm)

        if (!scheduleNextAlarm()) {
            alarmDao.delete(newAlarm.copy(id = newId.toInt()))
            return false
        }
        return true
    }

    override suspend fun toggleAlarm(alarmId: Int, isActive: Boolean): Boolean {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return true
        val updatedAlarm = alarm.copy(isActive = isActive)
        alarmDao.update(updatedAlarm)

        if (!scheduleNextAlarm()) {
            // Permission denied - revert the change and signal failure
            alarmDao.update(alarm)
            return false
        }
        return true
    }

    override suspend fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int): Boolean {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return true
        val updatedAlarm = alarm.copy(hour = hour, minute = minute, isActive = true)
        alarmDao.update(updatedAlarm)

        if (!scheduleNextAlarm()) {
            // Permission denied - revert the change and signal failure
            alarmDao.update(alarm)
            return false
        }
        return true
    }

    override suspend fun deleteAlarm(alarmId: Int) {
        alarmDao.getAlarmById(alarmId)?.let { alarm ->
            alarmDao.delete(alarm)
            scheduleNextAlarm()
        }
    }

    override suspend fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val updatedAlarm = alarm.copy(repeatDays = repeatDays)
        alarmDao.update(updatedAlarm)
        scheduleNextAlarm()
    }

    override suspend fun scheduleNextAlarm(): Boolean {
        val activeAlarms = alarmDao.getActiveAlarms()

        if (activeAlarms.isEmpty()) {
            cancelNextAlarm()
            setBootReceiverEnabled(false)
            return true
        }

        if (!canScheduleExactAlarms()) {
            return false
        }

        val alarmTriggers = activeAlarms.map { alarm ->
            alarm to calculateNextTrigger(alarm.hour, alarm.minute, alarm.repeatDays)
        }

        val minTriggerTime = alarmTriggers.minOf { it.second }

        val alarmIds = alarmTriggers
            .filter { it.second == minTriggerTime }
            .map { it.first.id }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent =
            Intent(context, AlarmReceiver::class.java).apply {
                putIntegerArrayListExtra("alarm_ids", ArrayList(alarmIds))
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                NEXT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val showIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(minTriggerTime, showIntent),
            pendingIntent,
        )
        setBootReceiverEnabled(true)
        Log.d("AlarmScheduler", "Scheduled next alarm (IDs=$alarmIds) at $minTriggerTime")
        return true
    }

    override suspend fun isAlarmRinging(): Boolean = prefs.getBoolean(KEY_IS_RINGING, false)

    override suspend fun setRingingAlarm() {
        prefs.edit().putBoolean(KEY_IS_RINGING, true).apply()
    }

    override suspend fun clearRingingAlarm() {
        prefs.edit().putBoolean(KEY_IS_RINGING, false).apply()
    }

    override suspend fun deactivateOneShotAlarms(ids: List<Int>) {
        alarmDao.deactivateOneShotAlarms(ids)
    }

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun setBootReceiverEnabled(enabled: Boolean) {
        val receiver = ComponentName(context, BootReceiver::class.java)
        context.packageManager.setComponentEnabledSetting(
            receiver,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun cancelNextAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                NEXT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNextTrigger(hour: Int, minute: Int, repeatDays: Set<Int>): Long {
        val now = Calendar.getInstance()
        val alarmTime =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        if (repeatDays.isEmpty()) {
            if (alarmTime.before(now)) {
                alarmTime.add(Calendar.DAY_OF_MONTH, 1)
            }
            return alarmTime.timeInMillis
        }

        for (i in 0 until 7) {
            val potentialNextDay = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, i) }
            val dayOfWeek = potentialNextDay[Calendar.DAY_OF_WEEK]

            if (dayOfWeek in repeatDays) {
                val triggerTime =
                    Calendar.getInstance().apply {
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

        var firstDayOfWeek = 8
        for (day in repeatDays) {
            if (day < firstDayOfWeek) {
                firstDayOfWeek = day
            }
        }

        val nextWeekAlarm =
            Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, 1)
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return nextWeekAlarm.timeInMillis
    }
}
