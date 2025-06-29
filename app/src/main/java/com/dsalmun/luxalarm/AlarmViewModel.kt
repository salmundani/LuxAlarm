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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.IAlarmRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(private val repository: IAlarmRepository) : ViewModel() {
    val alarms: StateFlow<List<AlarmItem>> =
        repository
            .getAllAlarms()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    fun addAlarm(hour: Int, minute: Int) {
        viewModelScope.launch {
            if (repository.addAlarm(hour, minute)) {
                _events.emit(Event.ShowAlarmSetMessage(hour, minute, emptySet()))
            } else {
                _events.emit(Event.ShowPermissionError)
            }
        }
    }

    fun toggleAlarm(alarmId: Int, isActive: Boolean) {
        viewModelScope.launch {
            if (repository.toggleAlarm(alarmId, isActive)) {
                if (isActive) {
                    val alarm = alarms.value.find { it.id == alarmId }
                    if (alarm != null) {
                        _events.emit(
                            Event.ShowAlarmSetMessage(alarm.hour, alarm.minute, alarm.repeatDays)
                        )
                    }
                }
            } else {
                _events.emit(Event.ShowPermissionError)
            }
        }
    }

    fun updateAlarmTime(alarmId: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            if (repository.updateAlarmTime(alarmId, hour, minute)) {
                val alarm = alarms.value.find { it.id == alarmId }
                _events.emit(
                    Event.ShowAlarmSetMessage(hour, minute, alarm?.repeatDays ?: emptySet())
                )
            } else {
                _events.emit(Event.ShowPermissionError)
            }
        }
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch { repository.deleteAlarm(alarmId) }
    }

    fun setRepeatDays(alarmId: Int, repeatDays: Set<Int>) {
        viewModelScope.launch {
            repository.setRepeatDays(alarmId, repeatDays)
            val alarm = alarms.value.find { it.id == alarmId }
            if (alarm != null && alarm.isActive) {
                _events.emit(Event.ShowAlarmSetMessage(alarm.hour, alarm.minute, repeatDays))
            }
        }
    }

    sealed class Event {
        data class ShowAlarmSetMessage(val hour: Int, val minute: Int, val repeatDays: Set<Int>) :
            Event()

        data object ShowPermissionError : Event()
    }
}

class AlarmViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AlarmViewModel(AppContainer.repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
