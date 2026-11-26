package com.kappacs.ontime.audio

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kappacs.ontime.databinding.ActivityAlarmBinding
import com.kappacs.ontime.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)

        setupAccessibilityFallbacks()
        setupSwipeControls()
        observeAlarmState()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupAccessibilityFallbacks() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isTouchExplorationEnabled = am?.isTouchExplorationEnabled == true

        if (isTouchExplorationEnabled) {
            binding.layoutAccessibilityButtons.visibility = View.VISIBLE
            binding.btnAccessibilityDismiss.setOnClickListener {
                dismissAlarm()
            }
            binding.btnAccessibilitySnooze.setOnClickListener {
                snoozeAlarm()
            }
        }
    }

    private fun setupSwipeControls() {
        lifecycleScope.launch {
            val hapticEnabled = settingsRepository.hapticFeedbackEnabled.first()
            binding.swipeActionView.hapticsEnabled = hapticEnabled
        }

        binding.swipeActionView.onDismissListener = {
            dismissAlarm()
        }

        binding.swipeActionView.onSnoozeListener = {
            snoozeAlarm()
        }
    }

    private fun observeAlarmState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.hapticFeedbackEnabled.collect { enabled ->
                        binding.swipeActionView.hapticsEnabled = enabled
                    }
                }
                launch {
                    settingsRepository.alarmRinging.collect { isRinging ->
                        if (!isRinging) {
                            finishAndRemoveTask()
                        }
                    }
                }
            }
        }
    }

    private fun dismissAlarm() {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_DISMISS_ALARM
        }
        startService(intent)
        finishAndRemoveTask()
    }

    private fun snoozeAlarm() {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_SNOOZE_ALARM
        }
        startService(intent)
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent accidental dismissal via back button while groggy
        // Alarm must be explicitly snoozed or dismissed via swipe gesture
    }
}
