package com.kappacs.ontime.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.kappacs.ontime.audio.AudioPlaybackService
import com.kappacs.ontime.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepository: SettingsRepository

    private var currentBgUri: String? = null
    private var currentAlarmUri: String? = null

    private val bgSoundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            lifecycleScope.launch {
                settingsRepository.setBgSoundUri(uri?.toString())
            }
        }
    }

    private val alarmSoundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            lifecycleScope.launch {
                settingsRepository.setAlarmSoundUri(uri?.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)

        setupClickListeners()
        setupTextChangeListeners()
        observeSettings()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectBgSound.setOnClickListener {
            pickSound(RingtoneManager.TYPE_RINGTONE, "Select Background Sound", currentBgUri, bgSoundPickerLauncher)
        }

        binding.btnSelectAlarmSound.setOnClickListener {
            pickSound(RingtoneManager.TYPE_ALARM, "Select Alarm Sound", currentAlarmUri, alarmSoundPickerLauncher)
        }

        binding.btnPlayAlarm.setOnClickListener {
            val intent = Intent(this, AudioPlaybackService::class.java).apply {
                action = AudioPlaybackService.ACTION_START_ALARM
                putExtra(AudioPlaybackService.EXTRA_FADE_MS, 5000L) // 5 seconds fade for testing
            }
            startService(intent)
        }

        binding.btnStopAlarm.setOnClickListener {
            val intent = Intent(this, AudioPlaybackService::class.java).apply {
                action = AudioPlaybackService.ACTION_DISMISS_ALARM
            }
            startService(intent)
        }

        binding.switchHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsRepository.setHapticFeedbackEnabled(isChecked)
            }
        }
    }

    private fun pickSound(
        type: Int,
        title: String,
        currentUriString: String?,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val currentUri = currentUriString?.let { Uri.parse(it) }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        launcher.launch(intent)
    }

    private fun setupTextChangeListeners() {
        binding.editBgFadeTime.doAfterTextChanged { text ->
            val seconds = text.toString().toIntOrNull() ?: 0
            lifecycleScope.launch {
                settingsRepository.setBgFadeTimeSec(seconds)
            }
        }

        binding.editAlarmSnoozeTime.doAfterTextChanged { text ->
            val minutes = text.toString().toIntOrNull() ?: 5
            lifecycleScope.launch {
                settingsRepository.setAlarmSnoozeMin(minutes)
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.bgSoundUri.collect { uri ->
                currentBgUri = uri
                binding.textBgSoundSource.text = getSoundName(uri, "Default Ringtone")
            }
        }

        lifecycleScope.launch {
            settingsRepository.alarmSoundUri.collect { uri ->
                currentAlarmUri = uri
                binding.textAlarmSoundSource.text = getSoundName(uri, "Default Alarm")
            }
        }

        lifecycleScope.launch {
            settingsRepository.bgFadeTimeSec.collect { seconds ->
                // Only update text if it's different to avoid cursor jumping while typing
                val currentText = binding.editBgFadeTime.text.toString()
                if (currentText.toIntOrNull() != seconds) {
                    binding.editBgFadeTime.setText(seconds.toString())
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.alarmSnoozeMin.collect { minutes ->
                // Only update text if it's different to avoid cursor jumping while typing
                val currentText = binding.editAlarmSnoozeTime.text.toString()
                if (currentText.toIntOrNull() != minutes) {
                    binding.editAlarmSnoozeTime.setText(minutes.toString())
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.alarmRinging.collect { ringing ->
                binding.btnPlayAlarm.isEnabled = !ringing
                binding.btnStopAlarm.isEnabled = ringing
            }
        }

        lifecycleScope.launch {
            settingsRepository.hapticFeedbackEnabled.collect { enabled ->
                if (binding.switchHapticFeedback.isChecked != enabled) {
                    binding.switchHapticFeedback.isChecked = enabled
                }
            }
        }
    }

    private fun getSoundName(uriString: String?, defaultName: String): String {
        if (uriString.isNullOrEmpty()) return defaultName
        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.getTitle(this) ?: defaultName
        } catch (e: Exception) {
            defaultName
        }
    }
}
