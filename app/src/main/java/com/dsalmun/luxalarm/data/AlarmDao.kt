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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Insert suspend fun insert(alarm: AlarmItem): Long

    @Update suspend fun update(alarm: AlarmItem)

    @Delete suspend fun delete(alarm: AlarmItem)

    @Query("SELECT * FROM alarms ORDER BY id ASC") fun getAllAlarms(): Flow<List<AlarmItem>>

    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun getAlarmById(id: Int): AlarmItem?

    @Query("SELECT * FROM alarms WHERE isActive = 1") suspend fun getActiveAlarms(): List<AlarmItem>

    @Query("SELECT id FROM alarms") suspend fun getAllAlarmIds(): List<Int>

    @Query("UPDATE alarms SET isActive = 0 WHERE id IN (:ids) AND repeatDays = ''")
    suspend fun deactivateOneShotAlarms(ids: List<Int>)
}
