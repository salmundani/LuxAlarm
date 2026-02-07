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

import android.content.Context
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmRepository
import com.dsalmun.luxalarm.data.IAlarmRepository

object AppContainer {
    @Volatile lateinit var database: AlarmDatabase

    @Volatile lateinit var repository: IAlarmRepository
    lateinit var settingsManager: SettingsManager

    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        database = AlarmDatabase.getDatabase(context)
        repository = AlarmRepository(database.alarmDao(), AlarmScheduler, context)
        settingsManager = SettingsManager(context)
        initialized = true
    }
}
