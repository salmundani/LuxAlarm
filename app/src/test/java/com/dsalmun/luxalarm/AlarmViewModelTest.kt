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

import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.IAlarmRepository
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AlarmViewModel
    private lateinit var fakeRepository: FakeAlarmRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAlarmRepository()
        viewModel = AlarmViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addAlarm_callsRepositoryAndHandlesSuccess() = runTest {
        val hour = 6
        val minute = 30
        fakeRepository.setShouldSucceed(true)

        viewModel.addAlarm(hour, minute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.addAlarmCallCount)
    }

    @Test
    fun addAlarm_callsRepositoryAndEmitsEventOnPermissionError() = runTest {
        val hour = 6
        val minute = 30
        fakeRepository.setShouldSucceed(false)

        val collectedEvents = mutableListOf<AlarmViewModel.Event>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { collectedEvents.add(it) }
            }

        viewModel.addAlarm(hour, minute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, collectedEvents.size)
        assertEquals(AlarmViewModel.Event.ShowPermissionError, collectedEvents[0])

        job.cancel()
        assertEquals(1, fakeRepository.addAlarmCallCount)
    }

    @Test
    fun toggleAlarm_callsRepository() = runTest {
        val alarmId = 1
        val isActive = true
        fakeRepository.setShouldSucceed(true)

        viewModel.toggleAlarm(alarmId, isActive)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.toggleAlarmCallCount)
    }

    @Test
    fun deleteAlarm_callsRepository() = runTest {
        val alarmId = 1
        viewModel.deleteAlarm(alarmId)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeRepository.deleteAlarmCallCount)
    }

    @Test
    fun updateAlarmTime_callsRepository() = runTest {
        val alarmId = 1
        val hour = 10
        val minute = 30
        fakeRepository.setShouldSucceed(true)

        viewModel.updateAlarmTime(alarmId, hour, minute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.updateAlarmTimeCallCount)
    }

    @Test
    fun setRepeatDays_callsRepository() = runTest {
        val alarmId = 1
        val repeatDays = setOf(1, 2, 3)

        viewModel.setRepeatDays(alarmId, repeatDays)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.setRepeatDaysCallCount)
    }

    @Test
    fun alarms_stateFlowCollectsFromRepository() = runTest {
        val fakeAlarms = listOf(AlarmItem(id = 1, hour = 8, minute = 0))
        val newViewModel = AlarmViewModel(fakeRepository)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { newViewModel.alarms.collect {} }

        fakeRepository.emit(fakeAlarms)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(fakeAlarms, newViewModel.alarms.value)

        job.cancel()
    }
}

class FakeAlarmRepository : IAlarmRepository {
    private val alarmsFlow = MutableStateFlow<List<AlarmItem>>(emptyList())
    private var shouldSucceed = true

    var addAlarmCallCount = 0
    var toggleAlarmCallCount = 0
    var deleteAlarmCallCount = 0
    var updateAlarmTimeCallCount = 0
    var setRepeatDaysCallCount = 0

    fun setShouldSucceed(succeed: Boolean) {
        shouldSucceed = succeed
    }

    suspend fun emit(alarms: List<AlarmItem>) {
        alarmsFlow.emit(alarms)
    }

    override fun getAllAlarms(): Flow<List<AlarmItem>> = alarmsFlow

    override suspend fun addAlarm(hour: Int, minute: Int): Boolean {
        addAlarmCallCount++
        return shouldSucceed
    }

    override suspend fun toggleAlarm(alarmId: Int, isActive: Boolean): Boolean {
        toggleAlarmCallCount++
        return shouldSucceed
    }

    override suspend fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int): Boolean {
        updateAlarmTimeCallCount++
        return shouldSucceed
    }

    override suspend fun deleteAlarm(alarmId: Int) {
        deleteAlarmCallCount++
    }

    override suspend fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {
        setRepeatDaysCallCount++
    }

    override suspend fun scheduleNextAlarm(): Boolean = shouldSucceed

    override fun canScheduleExactAlarms(): Boolean = true

    override suspend fun isAlarmRinging(): Boolean = false

    override suspend fun setRingingAlarm() {}

    override suspend fun clearRingingAlarm() {}

    override suspend fun deactivateOneShotAlarms(ids: List<Int>) {}
}
