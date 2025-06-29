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

import android.content.Context
import com.dsalmun.luxalarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val context: Context,
) : IAlarmRepository {
    override fun getAllAlarms(): Flow<List<AlarmItem>> = alarmDao.getAllAlarms()

    override suspend fun addAlarm(hour: Int, minute: Int): Boolean {
        val newAlarm = AlarmItem(hour = hour, minute = minute)
        val newId = alarmDao.insert(newAlarm)

        if (
            !alarmScheduler.scheduleExactAlarmAt(
                context,
                newAlarm.hour,
                newAlarm.minute,
                newId.toInt(),
                newAlarm.repeatDays,
            )
        ) {
            alarmDao.delete(newAlarm.copy(id = newId.toInt()))
            return false // Permission denied
        }
        return true
    }

    override suspend fun toggleAlarm(alarmId: Int, isActive: Boolean): Boolean {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return true // Or false? Alarm not found.
        val updatedAlarm = alarm.copy(isActive = isActive)
        alarmDao.update(updatedAlarm)

        if (isActive) {
            if (
                !alarmScheduler.scheduleExactAlarmAt(
                    context,
                    alarm.hour,
                    alarm.minute,
                    alarm.id,
                    alarm.repeatDays,
                )
            ) {
                // Permission denied - revert the change and signal failure
                alarmDao.update(alarm.copy(isActive = false))
                return false
            }
        } else {
            alarmScheduler.cancelAlarm(context, alarm.id)
        }
        return true
    }

    override suspend fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int): Boolean {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return true
        val updatedAlarm = alarm.copy(hour = hour, minute = minute, isActive = true)
        alarmDao.update(updatedAlarm)

        if (
            !alarmScheduler.scheduleExactAlarmAt(
                context,
                updatedAlarm.hour,
                updatedAlarm.minute,
                updatedAlarm.id,
                updatedAlarm.repeatDays,
            )
        ) {
            // Permission denied - revert the change and signal failure
            alarmDao.update(alarm.copy(isActive = false))
            return false
        }
        return true
    }

    override suspend fun deleteAlarm(alarmId: Int) {
        alarmDao.getAlarmById(alarmId)?.let { alarmToCancel ->
            alarmScheduler.cancelAlarm(context, alarmToCancel.id)
            alarmDao.delete(alarmToCancel)
        }
    }

    override suspend fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        val updatedAlarm = alarm.copy(repeatDays = repeatDays)
        alarmDao.update(updatedAlarm)
        // Reschedule the alarm with the updated repeat days
        if (updatedAlarm.isActive) {
            alarmScheduler.scheduleExactAlarmAt(
                context,
                updatedAlarm.hour,
                updatedAlarm.minute,
                updatedAlarm.id,
                updatedAlarm.repeatDays,
            )
        }
    }

    override suspend fun rescheduleAlarmAfterPlaying(alarmId: Int) {
        val alarm = alarmDao.getAlarmById(alarmId)
        if (alarm != null) {
            alarmScheduler.scheduleExactAlarmAt(
                context,
                alarm.hour,
                alarm.minute,
                alarm.id,
                alarm.repeatDays,
            )
        }
    }
}
