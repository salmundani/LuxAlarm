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
import com.dsalmun.luxalarm.data.AlarmItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms

    private val alarmScheduler = AlarmScheduler
    
    private val alarmDisableReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_DISABLE_ALARM) {
                val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
                if (alarmId != -1) {
                    disableAlarmAfterPlaying(alarmId)
                }
            }
        }
    }

    init {
        // Register the broadcast receiver to listen for alarm disable events
        val filter = IntentFilter(AlarmService.ACTION_DISABLE_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(alarmDisableReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                getApplication<Application>(),
                alarmDisableReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister the broadcast receiver when ViewModel is cleared
        try {
            getApplication<Application>().unregisterReceiver(alarmDisableReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was already unregistered
        }
    }

    private fun disableAlarmAfterPlaying(alarmId: Int) {
        viewModelScope.launch {
            _alarms.value = _alarms.value.map {
                if (it.id == alarmId) {
                    it.copy(isActive = false)
                } else {
                    it
                }
            }
        }
    }

    fun addAlarm(hour: Int, minute: Int) {
        viewModelScope.launch {
            val newAlarm = AlarmItem(hour = hour, minute = minute)
            if (alarmScheduler.scheduleExactAlarmAt(getApplication(), newAlarm.hour, newAlarm.minute, newAlarm.id)) {
                _alarms.value = _alarms.value + newAlarm
            } else {
                // Permission denied - show error
                Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleAlarm(alarmId: Int, isActive: Boolean) {
        viewModelScope.launch {
            _alarms.value = _alarms.value.map {
                if (it.id == alarmId) {
                    it.copy(isActive = isActive)
                } else {
                    it
                }
            }
            val alarm = _alarms.value.first { it.id == alarmId }
            if (isActive) {
                if (!alarmScheduler.scheduleExactAlarmAt(getApplication(), alarm.hour, alarm.minute, alarm.id)) {
                    // Permission denied - revert the change
                    _alarms.value = _alarms.value.map {
                        if (it.id == alarmId) {
                            it.copy(isActive = false)
                        } else {
                            it
                        }
                    }
                    Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
                }
            } else {
                alarmScheduler.cancelAlarm(getApplication(), alarm.id)
            }
        }
    }

    fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            _alarms.value = _alarms.value.map {
                if (it.id == alarmId) {
                    it.copy(hour = hour, minute = minute)
                } else {
                    it
                }
            }
            val updatedAlarm = _alarms.value.first { it.id == alarmId }
            if (updatedAlarm.isActive) {
                if (!alarmScheduler.scheduleExactAlarmAt(getApplication(), updatedAlarm.hour, updatedAlarm.minute, updatedAlarm.id)) {
                    Toast.makeText(getApplication(), "Cannot schedule exact alarms. Please grant permission in settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch {
            _alarms.value.find { it.id == alarmId }?.let { alarmToCancel ->
                alarmScheduler.cancelAlarm(getApplication(), alarmToCancel.id)
                _alarms.value = _alarms.value.filterNot { it.id == alarmId }
            }
        }
    }
} 