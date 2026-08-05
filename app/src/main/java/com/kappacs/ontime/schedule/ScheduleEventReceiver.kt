package com.kappacs.ontime.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kappacs.ontime.audio.AudioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleEventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive action: $action")

        if (action == ScheduleEngine.ACTION_TRIGGER_EVENT) {
            val eventId = intent.getLongExtra(ScheduleEngine.EXTRA_EVENT_ID, -1L)
            if (eventId == -1L) {
                Log.e(TAG, "Received trigger event with invalid ID")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = ScheduleRepository(context)
                    val event = repository.getEventById(eventId)
                    if (event != null && event.enabled) {
                        Log.d(TAG, "Triggering event: $event")
                        
                        // Forward to AudioPlaybackService
                        val serviceIntent = Intent(context, AudioPlaybackService::class.java).apply {
                            this.action = when (event.type) {
                                ScheduleEventType.START_ALARM -> AudioPlaybackService.ACTION_START_ALARM
                                ScheduleEventType.STOP_BG -> AudioPlaybackService.ACTION_STOP_BG
                            }
                            putExtra(AudioPlaybackService.EXTRA_FADE_MS, event.fadeTimeSec * 1000L)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)

                        // Reschedule or disable
                        if (event.daysOfWeek.isEmpty()) {
                            // One-time event: disable it
                            val disabledEvent = event.copy(enabled = false)
                            repository.updateEvent(disabledEvent)
                            Log.d(TAG, "One-time event ${event.id} completed and disabled")
                        } else {
                            // Repeating event: schedule next occurrence
                            ScheduleEngine.scheduleEvent(context, event)
                            Log.d(TAG, "Repeating event ${event.id} rescheduled for next occurrence")
                        }
                    } else {
                        Log.w(TAG, "Event $eventId not found or disabled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling trigger event", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
