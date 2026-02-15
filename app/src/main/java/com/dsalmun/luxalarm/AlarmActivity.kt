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

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsalmun.luxalarm.ui.theme.LuxAlarmTheme
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : ComponentActivity(), SensorEventListener {

    private var alarmId: Int = -1
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var currentLightLevel by mutableFloatStateOf(0f)
    private var requiredLightLevel by mutableFloatStateOf(SettingsManager.DEFAULT_LUX_LEVEL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    /* block back press while alarm is ringing */
                }
            },
        )

        alarmId = intent.getIntExtra("alarm_id", -1)
        requiredLightLevel = AppContainer.settingsManager.getRequiredLuxLevel()
        setupScreenWake()
        setupLightSensor()

        setContent {
            LuxAlarmTheme {
                AlarmRingingScreen(
                    currentLightLevel = currentLightLevel,
                    requiredLightLevel = requiredLightLevel,
                    onStopAlarm = { stopAlarm() },
                )
            }
        }
        setupFullscreen()
    }

    private fun setupLightSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            currentLightLevel = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No action needed for light sensor accuracy changes
    }

    private fun setupScreenWake() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.post {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    private fun stopAlarm() {
        val stopIntent =
            Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
                putExtra("alarm_id", alarmId)
            }
        startService(stopIntent)
        finish()
    }
}

@Composable
fun AlarmRingingScreen(
    currentLightLevel: Float,
    requiredLightLevel: Float,
    onStopAlarm: () -> Unit,
) {
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember {
        SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(Date())
    }
    val greeting = remember { getTimeBasedGreeting() }

    val gradientColors =
        listOf(
            Color(0xFF6366F1), // Soft indigo
            Color(0xFF8B5CF6), // Soft purple
            Color(0xFFA855F7), // Light purple
        )

    val isButtonEnabled = currentLightLevel >= requiredLightLevel

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = gradientColors,
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        )
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TimeDisplay(greeting, currentDate, currentTime)
            Spacer(modifier = Modifier.height(48.dp))
            LightSensorIndicator(currentLightLevel, requiredLightLevel, isButtonEnabled)
            AlarmControlButton(isButtonEnabled, onStopAlarm)
        }
    }
}

@Composable
private fun TimeDisplay(greeting: String, currentDate: String, currentTime: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = greeting,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentDate,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = currentTime,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LightSensorIndicator(
    currentLightLevel: Float,
    requiredLightLevel: Float,
    isButtonEnabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Light Level",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "${currentLightLevel.toInt()} lx",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isButtonEnabled) Color(0xFF10B981) else Color.White,
            )
            Text(
                text =
                    if (isButtonEnabled) "Bright enough!"
                    else "Need ${requiredLightLevel.toInt()} lx minimum",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            if (!isButtonEnabled) {
                Text(
                    text = "Go to a brighter area to turn off alarm",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AlarmControlButton(isButtonEnabled: Boolean, onStopAlarm: () -> Unit) {
    ElevatedButton(
        onClick = onStopAlarm,
        enabled = isButtonEnabled,
        modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors =
            ButtonDefaults.elevatedButtonColors(
                containerColor =
                    if (isButtonEnabled) Color.White.copy(alpha = 0.95f)
                    else Color.Gray.copy(alpha = 0.5f),
                contentColor =
                    if (isButtonEnabled) Color(0xFF6366F1) else Color.White.copy(alpha = 0.6f),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
        elevation =
            ButtonDefaults.elevatedButtonElevation(
                defaultElevation = if (isButtonEnabled) 8.dp else 2.dp,
                pressedElevation = if (isButtonEnabled) 12.dp else 2.dp,
                disabledElevation = 0.dp,
            ),
    ) {
        Text(
            text = if (isButtonEnabled) "Turn Off Alarm" else "Need More Light",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun getTimeBasedGreeting(): String {
    val calendar = Calendar.getInstance()
    val hour = calendar[Calendar.HOUR_OF_DAY]

    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        in 18..21 -> "Good Evening"
        else -> "Time to Wake Up" // Late night/early morning (22-4)
    }
}
