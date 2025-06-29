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
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsalmun.luxalarm.data.AlarmItem
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmScreen(alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModelFactory())) {
    val context = LocalContext.current
    val alarms by alarmViewModel.alarms.collectAsState()
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmItem?>(null) }
    var expandedAlarmId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(key1 = Unit) {
        alarmViewModel.events.collectLatest { event ->
            when (event) {
                is AlarmViewModel.Event.ShowPermissionError -> {
                    Toast.makeText(
                            context,
                            "Cannot schedule exact alarms. Please grant permission in settings.",
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                is AlarmViewModel.Event.ShowAlarmSetMessage -> {
                    showSetAlarmToast(context, event.hour, event.minute, event.repeatDays)
                }
            }
        }
    }

    val timePickerState =
        remember(alarmToEdit) {
            val calendar = Calendar.getInstance()
            val initialHour = alarmToEdit?.hour ?: calendar[Calendar.HOUR_OF_DAY]
            val initialMinute = alarmToEdit?.minute ?: calendar[Calendar.MINUTE]
            TimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = true,
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    alarmToEdit = null // Ensure we're in "add" mode
                    showTimePickerDialog = true
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Alarm")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Lux Alarm") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
    ) { innerPadding ->
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No alarms set. Tap '+' to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        expanded = expandedAlarmId == alarm.id,
                        onToggle = { isActive -> alarmViewModel.toggleAlarm(alarm.id, isActive) },
                        onClick = {
                            expandedAlarmId = if (expandedAlarmId == alarm.id) null else alarm.id
                        },
                        onTimeClick = {
                            alarmToEdit = alarm
                            showTimePickerDialog = true
                        },
                        onRepeatDaysChange = { newDays ->
                            alarmViewModel.setRepeatDays(alarm.id, newDays)
                        },
                    )
                }
            }
        }
    }

    if (showTimePickerDialog) {
        TimePickerDialog(
            onConfirm = {
                if (alarmToEdit != null) {
                    alarmViewModel.updateAlarmTime(
                        alarmToEdit!!.id,
                        timePickerState.hour,
                        timePickerState.minute,
                    )
                } else {
                    alarmViewModel.addAlarm(timePickerState.hour, timePickerState.minute)
                }
                showTimePickerDialog = false
                alarmToEdit = null
            },
            onDismiss = {
                showTimePickerDialog = false
                alarmToEdit = null
            },
            onDelete =
                if (alarmToEdit != null) {
                    {
                        alarmViewModel.deleteAlarm(alarmToEdit!!.id)
                        showTimePickerDialog = false
                        alarmToEdit = null
                    }
                } else null,
            timePickerState = timePickerState,
        )
    }
}

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onTimeClick: () -> Unit,
    onRepeatDaysChange: (Set<Int>) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onTimeClick),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector =
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                    Switch(checked = alarm.isActive, onCheckedChange = onToggle)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatRepeatDays(alarm.repeatDays, alarm.hour, alarm.minute),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                DaySelector(
                    selectedDays = alarm.repeatDays,
                    onDayClick = { day ->
                        val newDays = alarm.repeatDays.toMutableSet()
                        if (newDays.contains(day)) {
                            newDays.remove(day)
                        } else {
                            newDays.add(day)
                        }
                        onRepeatDaysChange(newDays)
                    },
                )
            }
        }
    }
}

@Composable
fun DaySelector(selectedDays: Set<Int>, onDayClick: (Int) -> Unit) {
    val days =
        listOf(
            "S" to Calendar.SUNDAY,
            "M" to Calendar.MONDAY,
            "T" to Calendar.TUESDAY,
            "W" to Calendar.WEDNESDAY,
            "T" to Calendar.THURSDAY,
            "F" to Calendar.FRIDAY,
            "S" to Calendar.SATURDAY,
        )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        days.forEach { (label, day) ->
            val isSelected = selectedDays.contains(day)
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onDayClick(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun formatRepeatDays(days: Set<Int>, hour: Int, minute: Int): String {
    if (days.isEmpty()) {
        val now = Calendar.getInstance()
        val alarmTime =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return if (alarmTime.after(now)) "Today" else "Tomorrow"
    }
    if (days.size == 7) return "Every day"

    val sortedDays = days.toSortedSet()
    val dayNames =
        sortedDays.map {
            when (it) {
                Calendar.SUNDAY -> "Sun"
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                else -> ""
            }
        }
    return dayNames.joinToString(", ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    timePickerState: TimePickerState,
    onDelete: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Alarm Time") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onConfirm) { Text("Set") }
            }
        },
    )
}

private fun showSetAlarmToast(
    context: Context,
    hour: Int,
    minute: Int,
    repeatDays: Set<Int> = emptySet(),
) {
    val scheduledTimeMillis = calculateNextTrigger(hour, minute, repeatDays)

    val now = Calendar.getInstance()
    val diffMillis = scheduledTimeMillis - now.timeInMillis
    val totalMinutes = kotlin.math.ceil(diffMillis / (1000.0 * 60)).toInt()
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    val timeParts = mutableListOf<String>()
    if (days > 0) {
        timeParts.add("$days ${if (days == 1) "day" else "days"}")
    }
    if (hours > 0) {
        timeParts.add("$hours ${if (hours == 1) "hour" else "hours"}")
    }
    if (minutes > 0) {
        timeParts.add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
    }
    if (timeParts.isEmpty()) {
        timeParts.add("less than a minute")
    }

    val toastMessage = "Alarm set for ${timeParts.joinToString(", ")} from now."
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

private fun calculateNextTrigger(hour: Int, minute: Int, repeatDays: Set<Int>): Long {
    val now = Calendar.getInstance()
    val alarmTime =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    if (repeatDays.isEmpty()) {
        if (alarmTime.before(now)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1) // schedule for next day if time has passed
        }
        return alarmTime.timeInMillis
    }

    // Find the next valid trigger time
    for (i in 0 until 7) {
        val potentialNextDay = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, i) }
        val dayOfWeek = potentialNextDay[Calendar.DAY_OF_WEEK]

        if (dayOfWeek in repeatDays) {
            val triggerTime =
                Calendar.getInstance().apply {
                    time = potentialNextDay.time
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            if (triggerTime.after(now)) {
                return triggerTime.timeInMillis
            }
        }
    }

    // If no time was found, it means the next alarm is next week. Find the first day of the week.
    var firstDayOfWeek = 8
    for (day in repeatDays) {
        if (day < firstDayOfWeek) {
            firstDayOfWeek = day
        }
    }

    val nextWeekAlarm =
        Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return nextWeekAlarm.timeInMillis
}
