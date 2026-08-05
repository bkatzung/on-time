package com.kappacs.ontime.schedule

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kappacs.ontime.databinding.ActivityScheduleListBinding
import com.kappacs.ontime.databinding.ItemScheduleEventBinding
import kotlinx.coroutines.launch

class ScheduleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleListBinding
    private lateinit var adapter: ScheduleAdapter
    private lateinit var repository: ScheduleRepository

    private val addEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val event = result.data?.getSerializableExtra(ScheduleEditorActivity.EXTRA_EVENT) as? ScheduleEvent
            if (event != null) {
                lifecycleScope.launch {
                    repository.insertEvent(event)
                }
            }
        }
    }

    private val editEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val event = result.data?.getSerializableExtra(ScheduleEditorActivity.EXTRA_EVENT) as? ScheduleEvent
            val isDelete = result.data?.getBooleanExtra(ScheduleEditorActivity.EXTRA_DELETE, false) ?: false
            
            if (event != null) {
                lifecycleScope.launch {
                    if (isDelete) {
                        repository.deleteEvent(event)
                    } else {
                        repository.updateEvent(event)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository(this)

        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            repository.getAllEventsFlow().collect { events ->
                updateUI(events)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onItemClick = { event ->
                val intent = Intent(this, ScheduleEditorActivity::class.java).apply {
                    putExtra(ScheduleEditorActivity.EXTRA_EVENT, event)
                }
                editEventLauncher.launch(intent)
            },
            onEnabledToggle = { event, isEnabled ->
                lifecycleScope.launch {
                    repository.updateEvent(event.copy(enabled = isEnabled))
                }
            }
        )
        binding.recyclerViewSchedule.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSchedule.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.fabAddEvent.setOnClickListener {
            val intent = Intent(this, ScheduleEditorActivity::class.java)
            addEventLauncher.launch(intent)
        }
    }

    private fun updateUI(events: List<ScheduleEvent>) {
        if (events.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerViewSchedule.visibility = View.GONE
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerViewSchedule.visibility = View.VISIBLE
            adapter.submitList(events)
        }
    }

    // Inner Adapter Class
    private class ScheduleAdapter(
        private val onItemClick: (ScheduleEvent) -> Unit,
        private val onEnabledToggle: (ScheduleEvent, Boolean) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        private var items = emptyList<ScheduleEvent>()

        fun submitList(newItems: List<ScheduleEvent>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemScheduleEventBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemScheduleEventBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(event: ScheduleEvent) {
                val context = binding.root.context
                binding.textTime.text = event.getFormattedTime(context)
                
                val typeStr = if (event.type == ScheduleEventType.START_ALARM) "Start Alarm" else "Stop Background"
                val detailsText = if (event.enabled) {
                    "$typeStr • ${event.getFormattedDays()} • Next: ${event.getFormattedNextTriggerTime(context)}"
                } else {
                    "$typeStr • ${event.getFormattedDays()}"
                }
                binding.textDetails.text = detailsText
                
                // Temporarily remove listener to avoid triggering it during binding
                binding.switchEnabled.setOnCheckedChangeListener(null)
                binding.switchEnabled.isChecked = event.enabled
                
                binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onEnabledToggle(event, isChecked)
                }

                binding.root.setOnClickListener {
                    onItemClick(event)
                }
            }
        }
    }
}
