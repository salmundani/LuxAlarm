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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmDatabase.Companion.MIGRATION_1_2
import com.dsalmun.luxalarm.data.AlarmItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@MediumTest
class MigrationTest {
    private lateinit var context: Context
    private val dbName = "migration_test_db"

    private data class V1Alarm(
        val id: Int,
        val hour: Int,
        val minute: Int,
        val isActive: Boolean,
        val repeatDays: String,
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV1Database(alarms: List<V1Alarm>) {
        val dbPath = context.getDatabasePath(dbName)
        dbPath.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `alarms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `repeatDays` TEXT NOT NULL
            )
            """
                .trimIndent()
        )
        for (alarm in alarms) {
            val values =
                ContentValues().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("isActive", if (alarm.isActive) 1 else 0)
                    put("repeatDays", alarm.repeatDays)
                }
            db.insert("alarms", null, values)
        }
        db.version = 1
        db.close()
    }

    private fun openV2Database(): AlarmDatabase =
        Room.databaseBuilder(context, AlarmDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    @Test
    fun migrate1To2_addsRingtoneUriColumn() {
        val v1Alarms =
            listOf(
                V1Alarm(id = 1, hour = 7, minute = 30, isActive = true, repeatDays = "1,2,3,4,5"),
                V1Alarm(id = 2, hour = 9, minute = 0, isActive = false, repeatDays = ""),
            )
        createV1Database(v1Alarms)

        val db = openV2Database()
        try {
            val alarms = runBlocking { db.alarmDao().getAllAlarms().first() }
            assertEquals(2, alarms.size)

            val alarm1 = alarms.first { it.id == 1 }
            assertEquals(7, alarm1.hour)
            assertEquals(30, alarm1.minute)
            assertEquals(true, alarm1.isActive)
            assertEquals(setOf(1, 2, 3, 4, 5), alarm1.repeatDays)
            assertNull(alarm1.ringtoneUri)

            val alarm2 = alarms.first { it.id == 2 }
            assertEquals(9, alarm2.hour)
            assertEquals(0, alarm2.minute)
            assertEquals(false, alarm2.isActive)
            assertEquals(emptySet(), alarm2.repeatDays)
            assertNull(alarm2.ringtoneUri)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate1To2_newRowsCanHaveRingtoneUri() {
        val v1Alarms =
            listOf(V1Alarm(id = 1, hour = 6, minute = 0, isActive = true, repeatDays = ""))
        createV1Database(v1Alarms)

        val db = openV2Database()
        try {
            val dao = db.alarmDao()
            runBlocking {
                dao.insert(
                    AlarmItem(hour = 8, minute = 15, ringtoneUri = "content://media/ringtone")
                )
            }

            val alarms = runBlocking { dao.getAllAlarms().first() }
            assertEquals(2, alarms.size)

            val migrated = alarms.first { it.id == 1 }
            assertNull(migrated.ringtoneUri)

            val newAlarm = alarms.first { it.id != 1 }
            assertEquals(8, newAlarm.hour)
            assertEquals(15, newAlarm.minute)
            assertEquals("content://media/ringtone", newAlarm.ringtoneUri)
        } finally {
            db.close()
        }
    }
}
