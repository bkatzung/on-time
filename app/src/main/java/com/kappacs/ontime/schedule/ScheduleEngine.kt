package com.kappacs.ontime.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kappacs.ontime.MainActivity
import java.util.Calendar

object ScheduleEngine {
    private const val TAG = "ScheduleEngine"
    
    const val ACTION_TRIGGER_EVENT = "com.kappacs.ontime.action.TRIGGER_EVENT"
    const val EXTRA_EVENT_ID = "com.kappacs.ontime.extra.EVENT_ID"

    fun scheduleEvent(context: Context, event: ScheduleEvent) {
        if (!event.enabled) {
            cancelEvent(context, event)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTimeMs = getNextTriggerTimeMs(event, System.currentTimeMillis())

        val intent = Intent(context, com.kappacs.ontime.audio.AudioPlaybackService::class.java).apply {
            action = ACTION_TRIGGER_EVENT
            putExtra(EXTRA_EVENT_ID, event.id)
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        Log.e(TAG, "SUCCESS: Scheduled event ${event.id} (${event.type}) for trigger time: $triggerTimeMs (${java.util.Date(triggerTimeMs)})")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (event.type == ScheduleEventType.START_ALARM) {
                val showIntent = Intent(context, MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }
    }

    fun cancelEvent(context: Context, event: ScheduleEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.kappacs.ontime.audio.AudioPlaybackService::class.java).apply {
            action = ACTION_TRIGGER_EVENT
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        }
        if (pendingIntent != null) {
            Log.d(TAG, "Canceling scheduled event ${event.id}")
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun getNextTriggerTimeMs(event: ScheduleEvent, nowMs: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, event.hour)
            set(Calendar.MINUTE, event.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (event.daysOfWeek.isEmpty()) {
            // Once
            if (calendar.timeInMillis <= nowMs) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        } else {
            // Repeating on specific days
            val currentDay = mapFromCalendarDay(Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_WEEK))
            
            var minDaysToAdd = 8
            for (day in event.daysOfWeek) {
                var daysToAdd = day - currentDay
                if (daysToAdd < 0) {
                    daysToAdd += 7
                } else if (daysToAdd == 0) {
                    val todayCalendar = Calendar.getInstance().apply {
                        timeInMillis = nowMs
                        set(Calendar.HOUR_OF_DAY, event.hour)
                        set(Calendar.MINUTE, event.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (todayCalendar.timeInMillis <= nowMs) {
                        daysToAdd = 7
                    }
                }
                if (daysToAdd < minDaysToAdd) {
                    minDaysToAdd = daysToAdd
                }
            }
            
            calendar.add(Calendar.DAY_OF_YEAR, minDaysToAdd)
            return calendar.timeInMillis
        }
    }

    private fun mapFromCalendarDay(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}
