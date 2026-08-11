package com.kappacs.ontime.audio

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kappacs.ontime.MainActivity
import com.kappacs.ontime.R
import com.kappacs.ontime.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AudioPlaybackService : Service() {

    companion object {
        private const val TAG = "AudioPlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_playback_channel"

        // Intent Actions
        const val ACTION_START_BG = "com.kappacs.ontime.action.START_BG"
        const val ACTION_STOP_BG = "com.kappacs.ontime.action.STOP_BG"
        const val ACTION_START_ALARM = "com.kappacs.ontime.action.START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.kappacs.ontime.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.kappacs.ontime.action.SNOOZE_ALARM"
        const val ACTION_TRIGGER_EVENT = "com.kappacs.ontime.action.TRIGGER_EVENT"

        // Intent Extras
        const val EXTRA_DURATION_MS = "com.kappacs.ontime.extra.DURATION_MS"
        const val EXTRA_FADE_MS = "com.kappacs.ontime.extra.FADE_MS"
        const val EXTRA_EVENT_ID = "com.kappacs.ontime.extra.EVENT_ID"
    }

    data class ServiceState(
        val isBgPlaying: Boolean = false,
        val bgDurationMs: Long = 0L,
        val bgTimeRemainingMs: Long = 0L,
        val isAlarmRinging: Boolean = false
    )

    private val _state = MutableStateFlow(ServiceState())
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private var bgPlayer: MediaPlayer? = null
    private var alarmPlayer: MediaPlayer? = null
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fadeController: FadeController
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var bgCountdownJob: Job? = null
    private var bgEndTime: Long = 0L

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        settingsRepository = SettingsRepository(this)
        fadeController = FadeController(serviceScope)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START_BG -> {
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                startBackgroundSound(durationMs)
            }
            ACTION_STOP_BG -> {
                val fadeMs = intent.getLongExtra(EXTRA_FADE_MS, 0L)
                stopBackgroundSound(fadeMs = fadeMs)
            }
            ACTION_START_ALARM -> {
                val fadeMs = intent.getLongExtra(EXTRA_FADE_MS, 0L)
                startAlarm(fadeMs = fadeMs)
            }
            ACTION_DISMISS_ALARM -> {
                dismissAlarm()
            }
            ACTION_SNOOZE_ALARM -> {
                snoozeAlarm()
            }
            ACTION_TRIGGER_EVENT -> {
                val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                if (eventId != -1L) {
                    triggerScheduledEvent(eventId)
                }
            }
        }

        updateForegroundNotification()
        return START_STICKY
    }

    private fun triggerScheduledEvent(eventId: Long) {
        serviceScope.launch {
            try {
                val scheduleRepository = com.kappacs.ontime.schedule.ScheduleRepository(applicationContext)
                val event = scheduleRepository.getEventById(eventId)
                if (event != null && event.enabled) {
                    Log.d(TAG, "Triggering scheduled event: $event")
                    
                    // Perform action
                    when (event.type) {
                        com.kappacs.ontime.schedule.ScheduleEventType.START_ALARM -> {
                            startAlarm(fadeMs = event.fadeTimeSec * 1000L)
                        }
                        com.kappacs.ontime.schedule.ScheduleEventType.STOP_BG -> {
                            stopBackgroundSound(fadeMs = event.fadeTimeSec * 1000L)
                        }
                    }

                    // Reschedule or disable
                    if (event.daysOfWeek.isEmpty()) {
                        val disabledEvent = event.copy(enabled = false)
                        scheduleRepository.updateEvent(disabledEvent)
                    } else {
                        com.kappacs.ontime.schedule.ScheduleEngine.scheduleEvent(applicationContext, event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering scheduled event", e)
            }
        }
    }

    private fun startBackgroundSound(durationMs: Long) {
        Log.d(TAG, "startBackgroundSound durationMs: $durationMs")
        acquireWakeLock()
        if (durationMs > 0) {
            _state.value = _state.value.copy(
                isBgPlaying = true,
                bgDurationMs = durationMs,
                bgTimeRemainingMs = durationMs
            )
        } else {
            _state.value = _state.value.copy(
                isBgPlaying = true,
                bgDurationMs = 0L,
                bgTimeRemainingMs = 0L
            )
        }
        serviceScope.launch {
            try {
                fadeController.cancelBgFade()
                bgCountdownJob?.cancel()
                bgCountdownJob = null

                bgPlayer?.release()
                
                val uriString = settingsRepository.bgSoundUri.first()
                val uri = if (!uriString.isNullOrEmpty()) Uri.parse(uriString) else Settings.System.DEFAULT_RINGTONE_URI
                
                bgPlayer = MediaPlayer().apply {
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(applicationContext, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    setVolume(1.0f, 1.0f)
                    start()
                }

                val fadeTimeSec = settingsRepository.bgFadeTimeSec.first()
                val fadeTimeMs = fadeTimeSec * 1000L

                if (durationMs > 0) {
                    bgEndTime = SystemClock.elapsedRealtime() + durationMs
                    startBgCountdown(fadeTimeMs)
                }
                
                // Persist background sound state
                val absoluteEndTime = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
                settingsRepository.setBgPlayingState(playing = true, durationMs = durationMs, endTime = absoluteEndTime)

                updateForegroundNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background sound", e)
                cleanupBgPlayer()
            }
        }
    }

    private fun startBgCountdown(fadeTimeMs: Long) {
        bgCountdownJob = serviceScope.launch {
            var fadeStarted = false
            while (true) {
                val remaining = bgEndTime - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    if (!fadeStarted) {
                        stopBackgroundSound(immediate = true)
                    }
                    break
                }
                
                if (fadeTimeMs > 0 && remaining <= fadeTimeMs && !fadeStarted) {
                    fadeStarted = true
                    Log.d(TAG, "Starting timed background sound fade-out over ${remaining}ms")
                    fadeController.fadeOut(bgPlayer, remaining) {
                        stopBackgroundSound(immediate = true)
                    }
                }

                _state.value = _state.value.copy(bgTimeRemainingMs = maxOf(0L, remaining))
                updateForegroundNotification()
                delay(1000)
            }
        }
    }

    private fun stopBackgroundSound(fadeMs: Long = 0L, immediate: Boolean = false) {
        Log.d(TAG, "stopBackgroundSound fadeMs: $fadeMs, immediate: $immediate")
        bgCountdownJob?.cancel()
        bgCountdownJob = null

        if (immediate || fadeMs <= 0L || bgPlayer == null) {
            fadeController.cancelBgFade()
            cleanupBgPlayer()
        } else {
            fadeController.fadeOut(bgPlayer, fadeMs) {
                cleanupBgPlayer()
            }
        }
    }

    private fun cleanupBgPlayer() {
        try {
            bgPlayer?.stop()
            bgPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing bgPlayer", e)
        } finally {
            bgPlayer = null
        }
        _state.value = _state.value.copy(
            isBgPlaying = false,
            bgDurationMs = 0L,
            bgTimeRemainingMs = 0L
        )
        serviceScope.launch {
            settingsRepository.setBgPlayingState(playing = false, durationMs = 0L, endTime = 0L)
        }
        checkStopSelf()
    }

    private fun startAlarm(fadeMs: Long) {
        Log.d(TAG, "startAlarm fadeMs: $fadeMs")
        acquireWakeLock()
        _state.value = _state.value.copy(isAlarmRinging = true)
        serviceScope.launch {
            try {
                fadeController.cancelAlarmFade()
                fadeController.cancelTransition()

                alarmPlayer?.release()
                alarmPlayer = null

                val uriString = settingsRepository.alarmSoundUri.first()
                val uri = if (!uriString.isNullOrEmpty()) Uri.parse(uriString) else Settings.System.DEFAULT_ALARM_ALERT_URI

                val isBgPlaying = _state.value.isBgPlaying

                if (isBgPlaying && bgPlayer != null && fadeMs > 0) {
                    fadeController.transitionBgToAlarm(
                        bgPlayer = bgPlayer,
                        transitionTimeMs = fadeMs,
                        onBgSilent = {
                            Log.d(TAG, "Transition: Background sound is now silent, cleaning up")
                            bgCountdownJob?.cancel()
                            bgCountdownJob = null
                            try {
                                bgPlayer?.stop()
                                bgPlayer?.release()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error stopping bgPlayer during transition", e)
                            } finally {
                                bgPlayer = null
                            }
                            _state.value = _state.value.copy(
                                isBgPlaying = false,
                                bgDurationMs = 0L,
                                bgTimeRemainingMs = 0L
                            )
                            serviceScope.launch {
                                settingsRepository.setBgPlayingState(playing = false, durationMs = 0L, endTime = 0L)
                            }
                        },
                        onFadeInAlarm = { actualFadeMs ->
                            Log.d(TAG, "Transition: Starting alarm fade-in over ${actualFadeMs}ms")
                            try {
                                alarmPlayer = MediaPlayer().apply {
                                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                                    try {
                                        setDataSource(applicationContext, uri)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to set custom alarm sound source during transition, falling back to default", e)
                                        setDataSource(applicationContext, Settings.System.DEFAULT_ALARM_ALERT_URI)
                                    }
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_ALARM)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                            .build()
                                    )
                                    isLooping = true
                                    prepare()
                                    setVolume(0f, 0f)
                                    start()
                                }
                                _state.value = _state.value.copy(isAlarmRinging = true)
                                updateForegroundNotification()

                                fadeController.fadeIn(alarmPlayer, actualFadeMs)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting alarm during transition", e)
                            }
                        }
                    )
                } else {
                    alarmPlayer = MediaPlayer().apply {
                        setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        try {
                            setDataSource(applicationContext, uri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set custom alarm sound source, falling back to default", e)
                            setDataSource(applicationContext, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        }
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        if (fadeMs > 0) {
                            setVolume(0f, 0f)
                        } else {
                            setVolume(1f, 1f)
                        }
                        start()
                    }

                    _state.value = _state.value.copy(isAlarmRinging = true)
                    updateForegroundNotification()

                    if (fadeMs > 0) {
                        fadeController.fadeIn(alarmPlayer, fadeMs)
                    }
                }
                
                // Persist alarm ringing state
                settingsRepository.setAlarmRingingState(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting alarm sound", e)
                _state.value = _state.value.copy(isAlarmRinging = false)
                checkStopSelf()
            }
        }
    }

    private fun dismissAlarm() {
        Log.d(TAG, "dismissAlarm")
        stopAlarmSound()
        cancelSnoozeAlarm()
        serviceScope.launch {
            settingsRepository.setAlarmSnoozedState(false, 0L)
        }
        checkStopSelf()
    }

    private fun snoozeAlarm() {
        Log.d(TAG, "snoozeAlarm")
        stopAlarmSound()
        
        serviceScope.launch {
            val snoozeMin = settingsRepository.alarmSnoozeMin.first()
            val snoozeMs = snoozeMin * 60 * 1000L
            val triggerTimeMs = System.currentTimeMillis() + snoozeMs
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val intent = Intent(applicationContext, AudioPlaybackService::class.java).apply {
                    action = ACTION_START_ALARM
                    putExtra(EXTRA_FADE_MS, 0L)
                }
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    9999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
                Log.d(TAG, "Snoozed alarm scheduled for $snoozeMin minutes from now")
            }
            
            // Persist snooze state
            settingsRepository.setAlarmSnoozedState(true, triggerTimeMs)
            
            checkStopSelf()
        }
    }

    private fun cancelSnoozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(applicationContext, AudioPlaybackService::class.java).apply {
            action = ACTION_START_ALARM
        }
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            9999,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            Log.d(TAG, "Canceling pending snooze alarm")
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun stopAlarmSound() {
        fadeController.cancelAlarmFade()
        fadeController.cancelTransition()
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm sound", e)
        } finally {
            alarmPlayer = null
        }
        _state.value = _state.value.copy(isAlarmRinging = false)
        serviceScope.launch {
            settingsRepository.setAlarmRingingState(false)
        }
    }

    private fun checkStopSelf() {
        val currentState = _state.value
        if (!currentState.isBgPlaying && !currentState.isAlarmRinging) {
            Log.d(TAG, "No active playback, stopping service")
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateForegroundNotification()
        }
    }

    private fun updateForegroundNotification() {
        val currentState = _state.value
        val notification = buildNotification(currentState)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ServiceState): Notification {
        val title = if (state.isAlarmRinging) {
            getString(R.string.alarm_status_ringing)
        } else {
            getString(R.string.app_name)
        }

        val text = when {
            state.isAlarmRinging -> "Alarm is active!"
            state.isBgPlaying && state.bgDurationMs > 0 -> {
                val minutes = (state.bgTimeRemainingMs / 1000) / 60
                val seconds = (state.bgTimeRemainingMs / 1000) % 60
                String.format("Background sound playing (%02d:%02d remaining)", minutes, seconds)
            }
            state.isBgPlaying -> "Background sound playing"
            else -> "On Time is running"
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (state.isAlarmRinging) {
            val dismissIntent = Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            val dismissPending = PendingIntent.getService(
                this, 1, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.alarm_dismiss),
                dismissPending
            )

            val snoozeIntent = Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_SNOOZE_ALARM
            }
            val snoozePending = PendingIntent.getService(
                this, 2, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                getString(R.string.alarm_snooze),
                snoozePending
            )
        } else if (state.isBgPlaying) {
            val stopIntent = Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP_BG
            }
            val stopPending = PendingIntent.getService(
                this, 3, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.bg_sound_stop),
                stopPending
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background sound and alarms"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OnTime:AudioPlaybackServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        bgCountdownJob?.cancel()
        fadeController.cancelAll()
        bgPlayer?.release()
        alarmPlayer?.release()
        releaseWakeLock()
        super.onDestroy()
    }
}
