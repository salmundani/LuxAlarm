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
package com.dsalmun.luxalarm.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromIntSet(value: Set<Int>): String = value.joinToString(separator = ",")

    @TypeConverter
    fun toIntSet(value: String): Set<Int> =
        if (value.isEmpty()) {
            emptySet()
        } else {
            value.split(',').map { it.toInt() }.toSet()
        }
}
