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
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.rule.ServiceTestRule
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.IAlarmRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
class AlarmServiceTest {
    @get:Rule val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var fakeRepository: FakeAlarmRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeAlarmRepository()
        AppContainer.repository = fakeRepository
    }

    @After
    fun tearDown() {
        val sharedPrefs = context.getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
    }

    @Test
    fun stopAlarm_callsRepositoryToReschedule_andClearsSharedPrefs() {
        val alarmId = 123
        val sharedPrefs = context.getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("alarm_ids", setOf(alarmId.toString())).commit()

        val intent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
        serviceRule.startService(intent)
        val success = fakeRepository.latch.await(5, TimeUnit.SECONDS)

        assertTrue(success, "Latch did not count down in time")
        assertEquals(1, fakeRepository.rescheduleAlarmAfterPlayingCallCount)
        assertEquals(alarmId, fakeRepository.rescheduledAlarmId)

        val playingAlarms = sharedPrefs.getStringSet("alarm_ids", null)
        assertNotNull(playingAlarms)
        assertTrue(playingAlarms.isEmpty(), "Alarm IDs should be cleared from SharedPreferences")
    }

    @Test
    fun stopAlarm_withNoPlayingAlarms_doesNotCallRepository() {
        val intent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
        serviceRule.startService(intent)

        val wasCalled = fakeRepository.latch.await(2, TimeUnit.SECONDS)

        assertFalse(wasCalled, "Repository method should not have been called")
        assertEquals(0, fakeRepository.rescheduleAlarmAfterPlayingCallCount)
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
    var rescheduleAlarmAfterPlayingCallCount = 0
    var rescheduledAlarmId = -1
    val latch = CountDownLatch(1)

    override fun getAllAlarms(): Flow<List<AlarmItem>> = alarmsFlow

    override suspend fun addAlarm(hour: Int, minute: Int): Boolean = true

    override suspend fun toggleAlarm(alarmId: Int, isActive: Boolean): Boolean = true

    override suspend fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int): Boolean = true

    override suspend fun deleteAlarm(alarmId: Int) {}

    override suspend fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {}

    override suspend fun rescheduleAlarmAfterPlaying(alarmId: Int) {
        rescheduleAlarmAfterPlayingCallCount++
        rescheduledAlarmId = alarmId
        latch.countDown()
    }
}
