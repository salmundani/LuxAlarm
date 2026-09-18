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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/** The real sensor plumbing: the type guard, the missing sensor, and the tied registration. */
@RunWith(AndroidJUnit4::class)
class LightSensorValueTest {
    private companion object {
        const val DARK = 12f
        const val BRIGHT = 480f
    }

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var composed by mutableStateOf(true)

    private val sensorManager: SensorManager
        get() =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test
    fun beforeAnyReadingArrives_theValueIsZero() {
        addLightSensor()

        setContent()

        assertReadingIs(0f)
    }

    @Test
    fun aLightReading_isReported() {
        addLightSensor()
        setContent()

        send(Sensor.TYPE_LIGHT, BRIGHT)

        assertReadingIs(BRIGHT)
    }

    @Test
    fun eachReadingReplacesTheLast() {
        addLightSensor()
        setContent()

        send(Sensor.TYPE_LIGHT, BRIGHT)
        send(Sensor.TYPE_LIGHT, DARK)

        assertReadingIs(DARK)
    }

    /** `onSensorChanged` is public, so the type guard is all that keeps out other readings. */
    @Test
    fun aReadingFromAnotherSensor_isIgnored() {
        addLightSensor()
        setContent()
        send(Sensor.TYPE_LIGHT, BRIGHT)

        send(Sensor.TYPE_PROXIMITY, DARK)

        assertReadingIs(BRIGHT)
    }

    /** Plenty of devices ship without an ambient light sensor; settings must still open. */
    @Test
    fun onADeviceWithNoLightSensor_nothingIsRegisteredAndTheValueStaysZero() {
        val before = listenerCount()

        setContent()

        assertEquals(before, listenerCount(), "Nothing to listen to, so nothing to register")
        assertReadingIs(0f)
    }

    @Test
    fun whileResumed_theSensorIsBeingListenedTo() {
        addLightSensor()
        val before = listenerCount()

        setContent()

        assertEquals(before + 1, listenerCount())
    }

    /** Asserted on the registry: a stopped activity has no semantics tree left to query. */
    @Test
    fun onPause_theListenerIsReleased() {
        addLightSensor()
        val before = listenerCount()
        setContent()
        assertEquals(before + 1, listenerCount(), "Precondition: listening")

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertEquals(before, listenerCount())
    }

    @Test
    fun onResume_theListenerIsRegisteredAgainAndReadingsResume() {
        addLightSensor()
        val before = listenerCount()
        setContent()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        send(Sensor.TYPE_LIGHT, BRIGHT)

        assertEquals(before + 1, listenerCount())
        assertReadingIs(BRIGHT)
    }

    /** The activity stays resumed, so `onDispose` runs alone and ON_PAUSE never does. */
    @Test
    fun whenTheComposableLeavesTheTree_theListenerIsReleased() {
        addLightSensor()
        val before = listenerCount()
        setContent()

        composed = false
        composeRule.waitForIdle()

        assertEquals(before, listenerCount())
    }

    private fun addLightSensor() {
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_LIGHT))
    }

    private fun listenerCount(): Int = shadowOf(sensorManager).listeners.size

    private fun setContent() {
        composeRule.setContent {
            if (composed) Text("lux=${rememberLightSensorValue()}") else Text("gone")
        }
    }

    private fun send(sensorType: Int, value: Float) {
        val event: SensorEvent =
            SensorEventBuilder.newBuilder()
                .setSensor(ShadowSensor.newInstance(sensorType))
                .setValues(floatArrayOf(value))
                .build()
        composeRule.runOnUiThread { shadowOf(sensorManager).sendSensorEventToListeners(event) }
        composeRule.waitForIdle()
    }

    private fun assertReadingIs(lux: Float, message: String? = null) {
        composeRule.onNodeWithText("lux=$lux").assertExists(message ?: "Expected a reading of $lux")
    }
}
