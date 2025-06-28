package com.dsalmun.luxalarm

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
    val alarms: StateFlow<List<AlarmItem>> = alarmDao.getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val alarmScheduler = AlarmScheduler

    private val alarmRescheduleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_RESCHEDULE_ALARM) {
                val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
                if (alarmId != -1) {
                    rescheduleAlarmAfterPlaying(alarmId)
                }
            }
        }
    }

    init {
        // Register the broadcast receiver to listen for alarm reschedule events
        val filter = IntentFilter(AlarmService.ACTION_RESCHEDULE_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(alarmRescheduleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                getApplication<Application>(),
                alarmRescheduleReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister the broadcast receiver when ViewModel is cleared
        try {
            getApplication<Application>().unregisterReceiver(alarmRescheduleReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was already unregistered
        }
    }

    private fun rescheduleAlarmAfterPlaying(alarmId: Int) {
        viewModelScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm != null) {
                alarmScheduler.scheduleExactAlarmAt(
                    getApplication(),
                    alarm.hour,
                    alarm.minute,
                    alarm.id,
                    alarm.repeatDays
                )
            }
        }
    }

    fun addAlarm(hour: Int, minute: Int) {
        viewModelScope.launch {
            val newAlarm = AlarmItem(hour = hour, minute = minute)
            val newId = alarmDao.insert(newAlarm)

            if (!alarmScheduler.scheduleExactAlarmAt(
                    getApplication(),
                    newAlarm.hour,
                    newAlarm.minute,
                    newId.toInt(),
                    newAlarm.repeatDays
                )
            ) {
                // Permission denied - show error
                Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
                alarmDao.delete(newAlarm.copy(id = newId.toInt()))
            }
        }
    }

    fun toggleAlarm(alarmId: Int, isActive: Boolean) {
        viewModelScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId) ?: return@launch
            val updatedAlarm = alarm.copy(isActive = isActive)
            alarmDao.update(updatedAlarm)

            if (isActive) {
                if (!alarmScheduler.scheduleExactAlarmAt(getApplication(), alarm.hour, alarm.minute, alarm.id, alarm.repeatDays)) {
                    // Permission denied - revert the change
                    alarmDao.update(alarm.copy(isActive = false))
                    Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
                }
            } else {
                alarmScheduler.cancelAlarm(getApplication(), alarm.id)
            }
        }
    }

    fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId) ?: return@launch
            val updatedAlarm = alarm.copy(hour = hour, minute = minute, isActive = true)
            alarmDao.update(updatedAlarm)

            if (!alarmScheduler.scheduleExactAlarmAt(getApplication(), updatedAlarm.hour, updatedAlarm.minute, updatedAlarm.id, updatedAlarm.repeatDays)) {
                // Permission denied - revert the change
                alarmDao.update(alarm.copy(isActive = false))
                Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch {
            alarmDao.getAlarmById(alarmId)?.let { alarmToCancel ->
                alarmScheduler.cancelAlarm(getApplication(), alarmToCancel.id)
                alarmDao.delete(alarmToCancel)
            }
        }
    }

    fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {
        viewModelScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId) ?: return@launch
            val updatedAlarm = alarm.copy(repeatDays = repeatDays)
            alarmDao.update(updatedAlarm)
            // Reschedule the alarm with the updated repeat days
            if (updatedAlarm.isActive) {
                alarmScheduler.scheduleExactAlarmAt(
                    getApplication(),
                    updatedAlarm.hour,
                    updatedAlarm.minute,
                    updatedAlarm.id,
                    updatedAlarm.repeatDays
                )
            }
        }
    }
} 