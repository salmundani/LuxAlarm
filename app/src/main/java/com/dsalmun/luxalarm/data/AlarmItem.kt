package com.dsalmun.luxalarm.data

import java.util.Calendar

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    val repeatDays: Set<Int> = emptySet()
) 