package com.kappacs.ontime

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kappacs.ontime.audio.AudioPlaybackService
import com.kappacs.ontime.databinding.ActivityMainBinding
import com.kappacs.ontime.schedule.ScheduleListActivity
import com.kappacs.ontime.schedule.ScheduleRepository
import com.kappacs.ontime.schedule.ScheduleEvent
import com.kappacs.ontime.schedule.ScheduleEventType
import com.kappacs.ontime.schedule.ScheduleEngine
import com.kappacs.ontime.settings.SettingsRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var audioService: AudioPlaybackService? = null
    private var isBound = false

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduleRepository: ScheduleRepository

    private var isAlarmRinging = false
    private var isAlarmSnoozed = false
    private var alarmSnoozeTriggerTime = 0L
    private var scheduleEvents = emptyList<ScheduleEvent>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission is required for background playback", Toast.LENGTH_LONG).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            android.util.Log.d("MainActivity", "onServiceConnected")
            val binder = service as AudioPlaybackService.LocalBinder
            audioService = binder.getService()
            isBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            android.util.Log.d("MainActivity", "onServiceDisconnected")
            audioService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        scheduleRepository = ScheduleRepository(this)

        setupClickListeners()
        checkNotificationPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.alarmRinging.collect { ringing ->
                        isAlarmRinging = ringing
                        updateAlarmUi()
                    }
                }
                launch {
                    settingsRepository.alarmSnoozed.collect { snoozed ->
                        isAlarmSnoozed = snoozed
                        updateAlarmUi()
                        checkSnoozeTicker()
                    }
                }
                launch {
                    settingsRepository.alarmSnoozeTriggerTime.collect { triggerTime ->
                        alarmSnoozeTriggerTime = triggerTime
                        updateAlarmUi()
                        checkSnoozeTicker()
                    }
                }
                launch {
                    scheduleRepository.getAllEventsFlow().collect { events ->
                        scheduleEvents = events
                        updateAlarmUi()
                    }
                }
            }
        }
    }

    private var snoozeTickerJob: kotlinx.coroutines.Job? = null

    private fun checkSnoozeTicker() {
        if (isAlarmSnoozed && alarmSnoozeTriggerTime > System.currentTimeMillis()) {
            if (snoozeTickerJob == null || snoozeTickerJob?.isActive == false) {
                snoozeTickerJob = lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isAlarmSnoozed && alarmSnoozeTriggerTime > System.currentTimeMillis()) {
                            updateAlarmUi()
                            kotlinx.coroutines.delay(1000)
                        }
                        snoozeTickerJob = null
                        updateAlarmUi()
                    }
                }
            }
        } else {
            snoozeTickerJob?.cancel()
            snoozeTickerJob = null
        }
    }

    private fun updateAlarmUi() {
        // 1. Calculate primary status: "Active", "Inactive", or "Snoozing for mm:ss"
        val primaryStatus = when {
            isAlarmRinging -> "Active"
            isAlarmSnoozed && alarmSnoozeTriggerTime > System.currentTimeMillis() -> {
                val remainingMs = alarmSnoozeTriggerTime - System.currentTimeMillis()
                val minutes = (remainingMs / 1000) / 60
                val seconds = (remainingMs / 1000) % 60
                String.format("Snoozing for %02d:%02d", minutes, seconds)
            }
            else -> "Inactive"
        }

        // 2. Calculate secondary status: "enabled: <n>"
        val enabledAlarmsCount = scheduleEvents.count { it.type == ScheduleEventType.START_ALARM && it.enabled }
        
        // Combine primary and secondary
        binding.textAlarmStatus.text = "$primaryStatus; enabled: $enabledAlarmsCount"

        // 3. Calculate tertiary status: "Next: <hh:mm>" if the next alarm will fire within 24 hours
        val now = System.currentTimeMillis()
        val nextAlarmTimeMs = scheduleEvents
            .filter { it.type == ScheduleEventType.START_ALARM && it.enabled }
            .map { ScheduleEngine.getNextTriggerTimeMs(it, now) }
            .minOrNull()

        if (nextAlarmTimeMs != null && nextAlarmTimeMs - now <= 24 * 60 * 60 * 1000L) {
            val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
            val formattedTime = if (is24Hour) {
                val sdf = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(nextAlarmTimeMs))
            } else {
                val sdf = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault())
                sdf.format(java.util.Date(nextAlarmTimeMs))
            }
            binding.textAlarmStatusTertiary.text = "Next: $formattedTime"
            binding.textAlarmStatusTertiary.visibility = View.VISIBLE
        } else {
            binding.textAlarmStatusTertiary.text = ""
            binding.textAlarmStatusTertiary.visibility = View.GONE
        }

        // Update button states based on ringing/snoozed state
        binding.btnAlarmDismiss.isEnabled = isAlarmRinging || isAlarmSnoozed
        binding.btnAlarmSnooze.isEnabled = isAlarmRinging
    }

    override fun onStart() {
        super.onStart()
        android.util.Log.d("MainActivity", "onStart - binding service")
        // Bind to AudioPlaybackService
        val intent = Intent(this, AudioPlaybackService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.d("MainActivity", "onStop - unbinding service")
        if (isBound) {
            unbindService(connection)
            isBound = false
            audioService = null
        }
    }

    override fun onDestroy() {
        android.util.Log.d("MainActivity", "onDestroy")
        super.onDestroy()
    }

    private fun setupClickListeners() {
        binding.btnBgPlay.setOnClickListener {
            startBackgroundSound()
        }

        binding.btnBgStop.setOnClickListener {
            sendCommandToService(AudioPlaybackService.ACTION_STOP_BG)
        }

        binding.btnAlarmDismiss.setOnClickListener {
            sendCommandToService(AudioPlaybackService.ACTION_DISMISS_ALARM)
        }

        binding.btnAlarmSnooze.setOnClickListener {
            sendCommandToService(AudioPlaybackService.ACTION_SNOOZE_ALARM)
        }

        binding.btnNavSchedule.setOnClickListener {
            val intent = Intent(this, ScheduleListActivity::class.java)
            startActivity(intent)
        }

        binding.btnNavSettings.setOnClickListener {
            val intent = Intent(this, com.kappacs.ontime.settings.SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startBackgroundSound() {
        val hoursStr = binding.editBgHours.text.toString()
        val minutesStr = binding.editBgMinutes.text.toString()

        val hours = hoursStr.toLongOrNull() ?: 0L
        val minutes = minutesStr.toLongOrNull() ?: 0L

        val durationMs = ((hours * 60) + minutes) * 60 * 1000

        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_START_BG
            putExtra(AudioPlaybackService.EXTRA_DURATION_MS, durationMs)
        }
        startService(intent)
    }

    private fun sendCommandToService(action: String) {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun observeServiceState() {
        val service = audioService ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service.state.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: AudioPlaybackService.ServiceState) {
        // Update Background Sound UI
        if (state.isBgPlaying) {
            if (state.bgDurationMs > 0) {
                val minutes = (state.bgTimeRemainingMs / 1000) / 60
                val seconds = (state.bgTimeRemainingMs / 1000) % 60
                binding.textBgSoundStatus.text = getString(R.string.bg_sound_status_playing_timed, minutes, seconds)
            } else {
                binding.textBgSoundStatus.text = getString(R.string.bg_sound_status_playing)
            }
            binding.btnBgPlay.isEnabled = false
            binding.btnBgStop.isEnabled = true
        } else {
            binding.textBgSoundStatus.text = getString(R.string.bg_sound_status_idle)
            binding.btnBgPlay.isEnabled = true
            binding.btnBgStop.isEnabled = false
        }

    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
