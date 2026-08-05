package com.kappacs.ontime.schedule

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

enum class ScheduleEventType {
    START_ALARM,
    STOP_BG
}

@Entity(tableName = "schedule_events")
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: ScheduleEventType = ScheduleEventType.START_ALARM,
    val hour: Int = 8,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = emptySet(), // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val fadeTimeSec: Int = 0,
    val enabled: Boolean = true
) : Serializable {
    
    fun isDaily(): Boolean = daysOfWeek.size == 7

    fun getFormattedTime(context: android.content.Context): String {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        return if (is24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val displayHour = if (hour == 0 || hour == 12) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d:%02d %s", displayHour, minute, amPm)
        }
    }

    fun getFormattedNextTriggerTime(context: android.content.Context): String {
        val now = System.currentTimeMillis()
        val nextTriggerMs = ScheduleEngine.getNextTriggerTimeMs(this, now)
        
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "EEE HH:mm" else "EEE h:mm a"
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        return sdf.format(java.util.Date(nextTriggerMs))
    }

    fun getFormattedDays(): String {
        if (daysOfWeek.isEmpty()) return "Once"
        if (daysOfWeek.size == 7) return "Daily"
        if (daysOfWeek.size == 5 && !daysOfWeek.contains(6) && !daysOfWeek.contains(7)) return "Weekdays"
        if (daysOfWeek.size == 2 && daysOfWeek.contains(6) && daysOfWeek.contains(7)) return "Weekends"
        
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return daysOfWeek.sorted().joinToString(", ") { dayNames[it - 1] }
    }
}
