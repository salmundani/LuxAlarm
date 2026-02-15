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
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _requiredLuxLevel = MutableStateFlow(getRequiredLuxLevel())
    val requiredLuxLevel: StateFlow<Float> = _requiredLuxLevel.asStateFlow()

    fun getRequiredLuxLevel(): Float {
        return prefs.getFloat(KEY_REQUIRED_LUX_LEVEL, DEFAULT_LUX_LEVEL)
    }

    fun setRequiredLuxLevel(level: Float) {
        prefs.edit { putFloat(KEY_REQUIRED_LUX_LEVEL, level) }
        _requiredLuxLevel.value = level
    }

    companion object {
        private const val PREFS_NAME = "lux_alarm_settings"
        private const val KEY_REQUIRED_LUX_LEVEL = "required_lux_level"
        const val DEFAULT_LUX_LEVEL = 50f
        const val MIN_LUX_LEVEL = 10f
        const val MAX_LUX_LEVEL = 1000f
    }
}
