package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.databinding.ItemLiveEventBinding
import com.phad.chatapp.models.AttendanceEvent
import android.view.View

class LiveEventsAdapter(
    private val events: List<AttendanceEvent>,
    private val onViewPendingClick: (AttendanceEvent) -> Unit,
    private val onCloseEventClick: (AttendanceEvent) -> Unit
) : RecyclerView.Adapter<LiveEventsAdapter.EventViewHolder>() {

    inner class EventViewHolder(private val binding: ItemLiveEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: AttendanceEvent) {
            binding.eventNameTextView.text = event.getEventName()
            binding.timestampTextView.text = "Event Date: ${event.getFormattedEventDate()}"
            binding.totalMarkedTextView.text = "Total Marked: ${event.totalMarked}"

            // Add time information if available
            val timeRange = event.getFormattedTimeRange()
            if (timeRange != "All Day") {
                binding.timestampTextView.text = "${binding.timestampTextView.text}\nTime: $timeRange"
            }

            // Update status display and button visibility based on event status
            // Note: Repository filtering ensures only live events (isLive=true) are shown here,
            // but we still check getEventStatus() for time-based status and pending submissions
            if (event.getEventStatus() == AttendanceEvent.STATUS_END) {
                binding.statusChip.text = "Ended"
                binding.statusChip.setChipBackgroundColorResource(com.google.android.material.R.color.design_default_color_error) // Or another color for ended
                binding.closeEventButton.visibility = View.GONE
            } else {
                binding.statusChip.text = "Live"
                binding.statusChip.setChipBackgroundColorResource(com.google.android.material.R.color.design_default_color_primary) // Or another color for live
                binding.closeEventButton.visibility = View.VISIBLE
            }
            
            binding.viewPendingButton.setOnClickListener {
                onViewPendingClick(event)
            }
            
            binding.closeEventButton.setOnClickListener {
                onCloseEventClick(event)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemLiveEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount() = events.size
} 