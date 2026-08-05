package com.kappacs.ontime.schedule

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDaysOfWeek(days: Set<Int>): String {
        return days.joinToString(",")
    }

    @TypeConverter
    fun toDaysOfWeek(data: String): Set<Int> {
        if (data.isEmpty()) return emptySet()
        return data.split(",").map { it.toInt() }.toSet()
    }

    @TypeConverter
    fun fromScheduleEventType(type: ScheduleEventType): String {
        return type.name
    }

    @TypeConverter
    fun toScheduleEventType(value: String): ScheduleEventType {
        return ScheduleEventType.valueOf(value)
    }
}
