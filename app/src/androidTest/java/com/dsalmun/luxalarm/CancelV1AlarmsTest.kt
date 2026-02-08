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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.AlarmRepository
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@MediumTest
class CancelV1AlarmsTest {
    private lateinit var context: Context
    private lateinit var database: AlarmDatabase
    private lateinit var repository: AlarmRepository

    private val testAlarmIds = mutableListOf<Int>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = AlarmRepository(database.alarmDao(), context)
        context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        database.close()
        for (id in testAlarmIds) {
            val intent = Intent(context, AlarmReceiver::class.java)
            PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.cancel()
        }
        context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun cancelV1Alarms_cancelsV1PendingIntents() {
        val dao = database.alarmDao()
        runBlocking {
            dao.insert(AlarmItem(id = 1, hour = 7, minute = 0, isActive = true))
            dao.insert(AlarmItem(id = 2, hour = 8, minute = 0, isActive = true))
        }
        testAlarmIds.addAll(listOf(1, 2))

        val intent = Intent(context, AlarmReceiver::class.java)
        for (id in testAlarmIds) {
            PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        for (id in testAlarmIds) {
            val existing = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            assertNotNull(existing, "v1 PendingIntent for alarm $id should exist before migration")
        }

        runBlocking { repository.cancelV1Alarms() }

        for (id in testAlarmIds) {
            val afterMigration = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            assertNull(afterMigration, "v1 PendingIntent for alarm $id should be cancelled")
        }

        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("v1_migrated", false), "v1_migrated flag should be true")
    }

    @Test
    fun cancelV1Alarms_skipsWhenAlreadyMigrated() {
        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("v1_migrated", true).apply()

        val dao = database.alarmDao()
        runBlocking {
            dao.insert(AlarmItem(id = 1, hour = 7, minute = 0, isActive = true))
        }
        testAlarmIds.add(1)

        val intent = Intent(context, AlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        runBlocking { repository.cancelV1Alarms() }

        val existing = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        assertNotNull(existing, "v1 PendingIntent should still exist when migration is skipped")
    }
}
