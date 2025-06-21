package com.dsalmun.luxalarm

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsalmun.luxalarm.data.AlarmItem
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmScreen(modifier: Modifier = Modifier, alarmViewModel: AlarmViewModel = viewModel()) {
    val context = LocalContext.current
    val alarms by alarmViewModel.alarms.collectAsState()
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmItem?>(null) }

    val timePickerState = rememberTimePickerState(is24Hour = true)

    // When the dialog is shown, initialize the time picker state
    LaunchedEffect(showTimePickerDialog, alarmToEdit) {
        if (showTimePickerDialog) {
            val calendar = Calendar.getInstance()
            val initialHour = alarmToEdit?.hour ?: calendar[Calendar.HOUR_OF_DAY]
            val initialMinute = alarmToEdit?.minute ?: calendar[Calendar.MINUTE]
            timePickerState.hour = initialHour
            timePickerState.minute = initialMinute
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    alarmToEdit = null // Ensure we're in "add" mode
                    showTimePickerDialog = true
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Alarm"
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("LuxAlarm") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    ) { innerPadding ->
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No alarms set. Tap '+' to add one.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { isActive ->
                            alarmViewModel.toggleAlarm(alarm.id, isActive)
                            if (isActive && AlarmScheduler.canScheduleExactAlarms(context)) {
                                showSetAlarmToast(context, alarm.hour, alarm.minute)
                            }
                        },
                        onClick = {
                            alarmToEdit = alarm
                            showTimePickerDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showTimePickerDialog) {
        TimePickerDialog(
            onConfirm = {
                if (alarmToEdit != null) {
                    alarmViewModel.updateAlarmTime(alarmToEdit!!.id, timePickerState.hour, timePickerState.minute)
                    if (alarmToEdit!!.isActive && AlarmScheduler.canScheduleExactAlarms(context)) {
                        showSetAlarmToast(context, timePickerState.hour, timePickerState.minute)
                    }
                } else {
                    alarmViewModel.addAlarm(timePickerState.hour, timePickerState.minute)
                    if (AlarmScheduler.canScheduleExactAlarms(context)) {
                        showSetAlarmToast(context, timePickerState.hour, timePickerState.minute)
                    }
                }
                showTimePickerDialog = false
                alarmToEdit = null
            },
            onDismiss = {
                showTimePickerDialog = false
                alarmToEdit = null
            },
            onDelete = if (alarmToEdit != null) {
                {
                    alarmViewModel.deleteAlarm(alarmToEdit!!.id)
                    showTimePickerDialog = false
                    alarmToEdit = null
                }
            } else null,
            timePickerState = timePickerState
        )
    }
}

private fun showSetAlarmToast(context: Context, hour: Int, minute: Int) {
    val now = Calendar.getInstance()
    val scheduledTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(now)) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val diffMillis = scheduledTime.timeInMillis - now.timeInMillis
    val totalMinutes = kotlin.math.ceil(diffMillis / (1000.0 * 60)).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    val timeParts = mutableListOf<String>()
    if (hours > 0) {
        timeParts.add("$hours ${if (hours == 1) "hour" else "hours"}")
    }
    if (minutes > 0 || timeParts.isEmpty()) {
        timeParts.add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
    }

    val toastMessage = "Alarm set for ${timeParts.joinToString(" and ")} from now."
    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
}

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = alarm.isActive,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    timePickerState: TimePickerState,
    onDelete: (() -> Unit)? = null
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Set")
                }
            }
        }
    )
} 