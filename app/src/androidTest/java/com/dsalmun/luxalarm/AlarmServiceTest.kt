package com.dsalmun.luxalarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.rule.ServiceTestRule
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.IAlarmRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
class AlarmServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

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
    fun stopAlarm_callsRepositoryToReschedule() {
        val alarmId = 123
        val sharedPrefs = context.getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putStringSet("alarm_ids", setOf(alarmId.toString()))
            .commit()

        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        serviceRule.startService(intent)
        val success = fakeRepository.latch.await(5, TimeUnit.SECONDS)

        assertTrue(success)
        assertEquals(fakeRepository.rescheduleAlarmAfterPlayingCallCount, 1)
        assertEquals(fakeRepository.rescheduledAlarmId, alarmId)
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