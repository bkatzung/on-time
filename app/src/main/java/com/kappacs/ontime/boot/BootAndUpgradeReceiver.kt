package com.kappacs.ontime.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kappacs.ontime.audio.AudioPlaybackService
import com.kappacs.ontime.schedule.ScheduleEngine
import com.kappacs.ontime.schedule.ScheduleRepository
import com.kappacs.ontime.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootAndUpgradeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootAndUpgradeReceiver"
        
        @Volatile
        private var hasProcessedBoot = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive action: $action")

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val isLockedBoot = action == Intent.ACTION_LOCKED_BOOT_COMPLETED
            val isBootCompleted = action == Intent.ACTION_BOOT_COMPLETED

            // If we already processed boot via LOCKED_BOOT_COMPLETED, and now we get BOOT_COMPLETED,
            // we only want to resume background sound. We don't want to reschedule alarms again.
            val alreadyProcessed = isBootCompleted && hasProcessedBoot

            if (isLockedBoot || isBootCompleted) {
                hasProcessedBoot = true
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settingsRepository = SettingsRepository(context)

                    if (!alreadyProcessed) {
                        // 1. Reschedule all enabled events
                        val scheduleRepository = ScheduleRepository(context)
                        val events = scheduleRepository.getAllEvents()
                        Log.d(TAG, "Found ${events.size} total events in database")
                        
                        var rescheduledCount = 0
                        for (event in events) {
                            if (event.enabled) {
                                ScheduleEngine.scheduleEvent(context, event)
                                rescheduledCount++
                            }
                        }
                        Log.e(TAG, "SUCCESS: Rescheduled $rescheduledCount enabled events on boot/upgrade")

                        // 2. Resume active alarm or reschedule snoozed alarm
                        val alarmRinging = settingsRepository.alarmRinging.first()
                        val alarmSnoozed = settingsRepository.alarmSnoozed.first()
                        val snoozeTriggerTime = settingsRepository.alarmSnoozeTriggerTime.first()

                        if (alarmRinging) {
                            Log.d(TAG, "Resuming active alarm")
                            val serviceIntent = Intent(context, AudioPlaybackService::class.java).apply {
                                this.action = AudioPlaybackService.ACTION_START_ALARM
                                putExtra(AudioPlaybackService.EXTRA_FADE_MS, 0L)
                            }
                            startServiceOrForegroundService(context, serviceIntent)
                        } else if (alarmSnoozed && snoozeTriggerTime > 0L) {
                            val now = System.currentTimeMillis()
                            if (snoozeTriggerTime <= now) {
                                Log.d(TAG, "Snoozed alarm trigger time has already passed. Triggering alarm immediately.")
                                val serviceIntent = Intent(context, AudioPlaybackService::class.java).apply {
                                    this.action = AudioPlaybackService.ACTION_START_ALARM
                                    putExtra(AudioPlaybackService.EXTRA_FADE_MS, 0L)
                                }
                                startServiceOrForegroundService(context, serviceIntent)
                            } else {
                                Log.d(TAG, "Rescheduling snoozed alarm for trigger time: $snoozeTriggerTime")
                                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                                if (alarmManager != null) {
                                    val serviceIntent = Intent(context, AudioPlaybackService::class.java).apply {
                                        this.action = AudioPlaybackService.ACTION_START_ALARM
                                        putExtra(AudioPlaybackService.EXTRA_FADE_MS, 0L)
                                    }
                                    val pendingIntent = PendingIntent.getService(
                                        context,
                                        9999,
                                        serviceIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                    
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTriggerTime, pendingIntent)
                                    } else {
                                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTriggerTime, pendingIntent)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Resume background sound if it was playing (ONLY if the device is unlocked / not locked boot)
                    if (!isLockedBoot) {
                        val bgPlaying = settingsRepository.bgPlaying.first()
                        if (bgPlaying) {
                            val bgDurationMs = settingsRepository.bgDurationMs.first()
                            val bgEndTime = settingsRepository.bgEndTime.first()
                            
                            val remainingMs = if (bgDurationMs > 0) {
                                bgEndTime - System.currentTimeMillis()
                            } else {
                                0L
                            }

                            if (bgDurationMs == 0L || remainingMs > 0) {
                                Log.d(TAG, "Resuming background sound. Duration: $bgDurationMs, Remaining: $remainingMs")
                                val serviceIntent = Intent(context, AudioPlaybackService::class.java).apply {
                                    this.action = AudioPlaybackService.ACTION_START_BG
                                    putExtra(AudioPlaybackService.EXTRA_DURATION_MS, if (bgDurationMs > 0) remainingMs else 0L)
                                }
                                startServiceOrForegroundService(context, serviceIntent)
                            } else {
                                Log.d(TAG, "Background sound was timed and has already expired. Clearing state.")
                                settingsRepository.setBgPlayingState(playing = false, durationMs = 0L, endTime = 0L)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring state on boot/upgrade", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun startServiceOrForegroundService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
