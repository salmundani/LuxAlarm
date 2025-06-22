package com.dsalmun.luxalarm.data

import android.content.Context

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    var isActive: Boolean = true
) {
    companion object {
        private const val ALARM_ID_COUNTER_PREF = "alarm_id_counter"
        private const val ID_START_VALUE = 0
        
        fun generateUniqueId(context: Context): Int {
            val sharedPrefs = context.getSharedPreferences("luxalarm_prefs", Context.MODE_PRIVATE)
            val currentId = sharedPrefs.getInt(ALARM_ID_COUNTER_PREF, ID_START_VALUE)
            sharedPrefs.edit().putInt(ALARM_ID_COUNTER_PREF, currentId + 1).apply()
            return currentId
        }
        
        fun create(context: Context, hour: Int, minute: Int, isActive: Boolean = true): AlarmItem {
            return AlarmItem(
                id = generateUniqueId(context),
                hour = hour,
                minute = minute,
                isActive = isActive
            )
        }
    }
} 