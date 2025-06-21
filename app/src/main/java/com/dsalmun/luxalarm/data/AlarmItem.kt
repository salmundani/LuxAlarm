package com.dsalmun.luxalarm.data

data class AlarmItem(
    val id: Int = System.currentTimeMillis().toInt(),
    val hour: Int,
    val minute: Int,
    var isActive: Boolean = true
) 