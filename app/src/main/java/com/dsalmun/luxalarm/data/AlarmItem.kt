package com.dsalmun.luxalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "alarms")
@TypeConverters(Converters::class)
data class AlarmItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    val repeatDays: Set<Int> = emptySet()
) 