package com.dsalmun.luxalarm.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntSet(value: Set<Int>): String {
        return value.joinToString(separator = ",")
    }

    @TypeConverter
    fun toIntSet(value: String): Set<Int> {
        return if (value.isEmpty()) {
            emptySet()
        } else {
            value.split(',').map { it.toInt() }.toSet()
        }
    }
} 