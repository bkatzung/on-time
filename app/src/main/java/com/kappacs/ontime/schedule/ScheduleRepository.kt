package com.kappacs.ontime.schedule

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val context: Context) {
    private val scheduleDao = AppDatabase.getDatabase(context).scheduleDao()

    fun getAllEventsFlow(): Flow<List<ScheduleEvent>> = scheduleDao.getAllEventsFlow()

    suspend fun getAllEvents(): List<ScheduleEvent> = scheduleDao.getAllEvents()

    suspend fun getEventById(id: Long): ScheduleEvent? = scheduleDao.getEventById(id)

    suspend fun insertEvent(event: ScheduleEvent): Long {
        val id = scheduleDao.insertEvent(event)
        val insertedEvent = event.copy(id = id)
        ScheduleEngine.scheduleEvent(context, insertedEvent)
        return id
    }

    suspend fun updateEvent(event: ScheduleEvent) {
        scheduleDao.updateEvent(event)
        if (event.enabled) {
            ScheduleEngine.scheduleEvent(context, event)
        } else {
            ScheduleEngine.cancelEvent(context, event)
        }
    }

    suspend fun deleteEvent(event: ScheduleEvent) {
        scheduleDao.deleteEvent(event)
        ScheduleEngine.cancelEvent(context, event)
    }
}
