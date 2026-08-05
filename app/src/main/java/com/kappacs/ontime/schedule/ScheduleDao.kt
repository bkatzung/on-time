package com.kappacs.ontime.schedule

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_events ORDER BY hour * 60 + minute ASC")
    fun getAllEventsFlow(): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events ORDER BY hour * 60 + minute ASC")
    suspend fun getAllEvents(): List<ScheduleEvent>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun getEventById(id: Long): ScheduleEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScheduleEvent): Long

    @Update
    suspend fun updateEvent(event: ScheduleEvent)

    @Delete
    suspend fun deleteEvent(event: ScheduleEvent)
}
