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

import android.app.Application
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmRepository
import com.dsalmun.luxalarm.data.IAlarmRepository

class AppContainer : Application() {
    companion object {
        @Volatile lateinit var database: AlarmDatabase

        @Volatile lateinit var repository: IAlarmRepository
        lateinit var settingsManager: SettingsManager
    }

    override fun onCreate() {
        super.onCreate()
        database = AlarmDatabase.getDatabase(this)
        repository = AlarmRepository(database.alarmDao(), this)
        settingsManager = SettingsManager(this)
    }
}
