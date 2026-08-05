package com.kappacs.ontime.schedule

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.kappacs.ontime.databinding.ActivityScheduleEditorBinding

class ScheduleEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT = "com.kappacs.ontime.schedule.EXTRA_EVENT"
        const val EXTRA_DELETE = "com.kappacs.ontime.schedule.EXTRA_DELETE"
    }

    private lateinit var binding: ActivityScheduleEditorBinding
    private var existingEvent: ScheduleEvent? = null
    private lateinit var dayCheckboxes: List<CheckBox>
    private var isUpdatingDaysProgrammatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingEvent = IntentCompat.getSerializableExtra(intent, EXTRA_EVENT, ScheduleEvent::class.java)

        dayCheckboxes = listOf(
            binding.checkMonday,
            binding.checkTuesday,
            binding.checkWednesday,
            binding.checkThursday,
            binding.checkFriday,
            binding.checkSaturday,
            binding.checkSunday
        )

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Respect system 24-hour format preference
        binding.timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        val event = existingEvent
        if (event != null) {
            binding.textTitle.text = "Edit Event"
            binding.btnDelete.visibility = View.VISIBLE

            // Set Time
            binding.timePicker.hour = event.hour
            binding.timePicker.minute = event.minute

            // Set Type
            if (event.type == ScheduleEventType.START_ALARM) {
                binding.radioStartAlarm.isChecked = true
            } else {
                binding.radioStopBg.isChecked = true
            }

            // Set Days
            isUpdatingDaysProgrammatically = true
            for (i in 1..7) {
                dayCheckboxes[i - 1].isChecked = event.daysOfWeek.contains(i)
            }
            binding.checkDaily.isChecked = event.isDaily()
            isUpdatingDaysProgrammatically = false

            // Set Fade Time
            binding.editFadeTime.setText(event.fadeTimeSec.toString())

            // Set Enabled
            binding.switchEnabled.isChecked = event.enabled
        } else {
            binding.textTitle.text = "Add Event"
            binding.btnDelete.visibility = View.GONE
            
            // Default to 8:00 AM
            binding.timePicker.hour = 8
            binding.timePicker.minute = 0
            binding.editFadeTime.setText("0")
            binding.switchEnabled.isChecked = true
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }


        // Daily checkbox listener
        binding.checkDaily.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingDaysProgrammatically) return@setOnCheckedChangeListener
            
            isUpdatingDaysProgrammatically = true
            dayCheckboxes.forEach { checkbox ->
                checkbox.isChecked = isChecked
            }
            isUpdatingDaysProgrammatically = false
        }

        // Individual day checkboxes listener
        dayCheckboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                if (isUpdatingDaysProgrammatically) return@setOnCheckedChangeListener
                
                isUpdatingDaysProgrammatically = true
                val allChecked = dayCheckboxes.all { it.isChecked }
                binding.checkDaily.isChecked = allChecked
                isUpdatingDaysProgrammatically = false
            }
        }

        binding.btnSave.setOnClickListener {
            saveEvent()
        }

        binding.btnDelete.setOnClickListener {
            deleteEvent()
        }
    }

    private fun saveEvent() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        
        val type = if (binding.radioStartAlarm.isChecked) {
            ScheduleEventType.START_ALARM
        } else {
            ScheduleEventType.STOP_BG
        }

        val daysOfWeek = mutableSetOf<Int>()
        for (i in 1..7) {
            if (dayCheckboxes[i - 1].isChecked) {
                daysOfWeek.add(i)
            }
        }

        val fadeTimeSec = binding.editFadeTime.text.toString().toIntOrNull() ?: 0
        val enabled = binding.switchEnabled.isChecked

        val event = ScheduleEvent(
            id = existingEvent?.id ?: 0L,
            type = type,
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            fadeTimeSec = fadeTimeSec,
            enabled = enabled
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_EVENT, event)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun deleteEvent() {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_EVENT, existingEvent)
            putExtra(EXTRA_DELETE, true)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

}
