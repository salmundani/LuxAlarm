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
import android.content.Intent
import android.os.IBinder
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.rule.ServiceTestRule
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.IAlarmRepository
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
class AlarmServiceTest {
    @get:Rule val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var fakeRepository: FakeAlarmRepository
    private lateinit var database: AlarmDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeAlarmRepository()
        database =
            Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppContainer.repository = fakeRepository
        AppContainer.database = database
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deactivateOneShotAlarms_onlyDeactivatesNonRepeating() {
        val dao = database.alarmDao()
        runBlocking {
            dao.insert(AlarmItem(id = 1, hour = 7, minute = 0, isActive = true, repeatDays = emptySet()))
            dao.insert(AlarmItem(id = 2, hour = 8, minute = 0, isActive = true, repeatDays = setOf(1, 2, 3)))
            dao.deactivateOneShotAlarms(listOf(1, 2))
        }

        val oneShot = runBlocking { dao.getAlarmById(1) }
        val repeating = runBlocking { dao.getAlarmById(2) }
        assertFalse(oneShot!!.isActive, "One-shot alarm should be deactivated")
        assertTrue(repeating!!.isActive, "Repeating alarm should remain active")
    }

    @Test
    fun serviceBinds() {
        val intent = Intent(context, AlarmService::class.java)
        val binder: IBinder = serviceRule.bindService(intent)
        val service = (binder as AlarmService.LocalBinder).getService()
        assertNotNull(service)
    }
}

class FakeAlarmRepository : IAlarmRepository {
    private val alarmsFlow = MutableStateFlow<List<AlarmItem>>(emptyList())
    override fun getAllAlarms(): Flow<List<AlarmItem>> = alarmsFlow

    override suspend fun addAlarm(hour: Int, minute: Int): Boolean = true

    override suspend fun toggleAlarm(alarmId: Int, isActive: Boolean): Boolean = true

    override suspend fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int): Boolean = true

    override suspend fun deleteAlarm(alarmId: Int) {}

    override suspend fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {}

    override suspend fun scheduleNextAlarm(): Boolean = true

    override fun canScheduleExactAlarms(): Boolean = true

    override fun isAlarmRinging(): Boolean = false

    override fun setRingingAlarm(): Boolean = true

    override fun clearRingingAlarm() {}

    override suspend fun deactivateOneShotAlarms(ids: List<Int>) {}

    override suspend fun cancelV1Alarms() {}
}
