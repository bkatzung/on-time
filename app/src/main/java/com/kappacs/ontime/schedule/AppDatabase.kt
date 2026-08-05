package com.kappacs.ontime.schedule

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ScheduleEvent::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val deContext = context.applicationContext.createDeviceProtectedStorageContext()
                val instance = Room.databaseBuilder(
                    deContext,
                    AppDatabase::class.java,
                    "on_time_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
