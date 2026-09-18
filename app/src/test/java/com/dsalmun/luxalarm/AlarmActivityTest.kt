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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dsalmun.luxalarm.testing.AppContainerTestRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/**
 * Drives the real activity, the only proof the light sensor reaches the screen. Rule order matters:
 * the listener is registered on resume, so a sensor added afterwards is never seen.
 */
@RunWith(AndroidJUnit4::class)
class AlarmActivityTest {
    private companion object {
        const val ALARM_ID = 7
        const val DARK = 10f
        const val BRIGHT = 300f
    }

    @get:Rule(order = 0) val appContainer = AppContainerTestRule()

    /** Robolectric keeps the sensor and listener registries static, so any manager sees these. */
    @get:Rule(order = 1)
    val lightSensor =
        object : TestWatcher() {
            override fun starting(description: Description) {
                shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_LIGHT))
            }
        }

    @get:Rule(order = 2)
    val composeRule =
        AndroidComposeTestRule(
            activityRule =
                ActivityScenarioRule<AlarmActivity>(
                    Intent(
                            ApplicationProvider.getApplicationContext<Context>(),
                            AlarmActivity::class.java,
                        )
                        .putExtra("alarm_id", ALARM_ID)
                ),
            activityProvider = { rule ->
                var activity: AlarmActivity? = null
                rule.scenario.onActivity { activity = it }
                checkNotNull(activity) { "AlarmActivity was never created" }
            },
        )

    private val activity
        get() = composeRule.activity

    private val sensorManager: SensorManager
        get() =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test
    fun theVolumeKeysAreSwallowedWhileTheAlarmRings() {
        for (keyCode in
            listOf(
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_MUTE,
            )) {
            assertTrue(
                activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
                "Key $keyCode must not reach the window and turn the alarm down",
            )
            assertTrue(activity.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode)))
        }
    }

    /** Swallowing everything would take the keys the screen itself still needs. */
    @Test
    fun otherKeysAreLeftAlone() {
        val keyCode = KeyEvent.KEYCODE_A
        assertFalse(activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
    }

    @Test
    fun withNoReadingYet_theAlarmCannotBeStopped() {
        composeRule.onNodeWithText("Need More Light").assertIsNotEnabled()
    }

    @Test
    fun aBrightReading_unlocksTheStopButton() {
        sendLightReading(BRIGHT)

        composeRule.onNodeWithText("Turn Off Alarm").assertIsEnabled()
    }

    @Test
    fun aDimReading_leavesTheStopButtonLocked() {
        sendLightReading(DARK)

        composeRule.onNodeWithText("Need More Light").assertIsNotEnabled()
    }

    @Test
    fun theButtonTracksTheLightLevelInBothDirections() {
        sendLightReading(BRIGHT)
        composeRule.onNodeWithText("Turn Off Alarm").assertIsEnabled()

        sendLightReading(DARK)
        composeRule.onNodeWithText("Need More Light").assertIsNotEnabled()
    }

    /** `onSensorChanged` is public, so the type guard is all that keeps proximity out. */
    @Test
    fun aReadingFromAnotherSensor_isIgnored() {
        sendLightReading(BRIGHT)
        composeRule.onNodeWithText("Turn Off Alarm").assertIsEnabled()

        sendReading(Sensor.TYPE_PROXIMITY, DARK)

        composeRule.onNodeWithText("Turn Off Alarm").assertIsEnabled()
    }

    @Test
    fun whileResumed_theActivityListensToTheLightSensor() {
        assertTrue(shadowOf(sensorManager).hasListener(activity))
    }

    @Test
    fun onPause_theListenerIsReleased() {
        val activity = activity
        assertTrue(shadowOf(sensorManager).hasListener(activity), "Precondition: listening")

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertFalse(shadowOf(sensorManager).hasListener(activity))
    }

    @Test
    fun onResume_theListenerIsRegisteredAgain() {
        val activity = activity

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        assertTrue(shadowOf(sensorManager).hasListener(activity))
    }

    @Test
    fun theScreenIsTurnedOnAndShownOverTheLockScreen() {
        assertTrue(shadowOf(activity).getShowWhenLocked(), "Must appear over the lock screen")
        assertTrue(shadowOf(activity).getTurnScreenOn(), "Must wake the device")
        assertTrue(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON !=
                0,
            "Must stay on while ringing",
        )
    }

    @Test
    fun stoppingTheAlarm_tellsTheServiceWhichAlarmToStopAndCloses() {
        sendLightReading(BRIGHT)

        composeRule.onNodeWithText("Turn Off Alarm").performClick()

        val stopIntent = assertNotNull(shadowOf(activity).nextStartedService)
        assertEquals(AlarmService::class.java.name, stopIntent.component?.className)
        assertEquals(AlarmService.ACTION_STOP_ALARM, stopIntent.action)
        assertEquals(ALARM_ID, stopIntent.getIntExtra("alarm_id", -1), "The launch id round-trips")
        assertTrue(activity.isFinishing, "The screen closes once the alarm is stopped")
    }

    /** Back is swallowed on purpose: a gesture dismissal would defeat the light requirement. */
    @Test
    fun back_doesNotDismissTheAlarm() {
        composeRule.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }

        assertFalse(activity.isFinishing)
    }

    private fun sendLightReading(lux: Float) = sendReading(Sensor.TYPE_LIGHT, lux)

    private fun sendReading(sensorType: Int, value: Float) {
        val event: SensorEvent =
            SensorEventBuilder.newBuilder()
                .setSensor(ShadowSensor.newInstance(sensorType))
                .setValues(floatArrayOf(value))
                .build()
        composeRule.runOnUiThread { shadowOf(sensorManager).sendSensorEventToListeners(event) }
        composeRule.waitForIdle()
    }
}
