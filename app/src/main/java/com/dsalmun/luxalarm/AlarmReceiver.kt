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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmIds = intent?.getIntegerArrayListExtra("alarm_ids") ?: arrayListOf()
        val alarmId = alarmIds.firstOrNull() ?: -1
        val ringtoneUri = intent?.getStringExtra("ringtone_uri")

        if (AppContainer.repository.setRingingAlarm()) {
            val serviceIntent =
                Intent(context, AlarmService::class.java).apply {
                    putExtra("alarm_id", alarmId)
                    putExtra("ringtone_uri", ringtoneUri)
                }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppContainer.repository.deactivateOneShotAlarms(alarmIds.toList())
                AppContainer.repository.scheduleNextAlarm()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
