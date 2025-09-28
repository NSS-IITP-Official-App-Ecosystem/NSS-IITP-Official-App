package com.phad.chatapp.features.calendar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.features.calendar.R
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.EventStatus
import android.util.Log

class EventAdapter(
    private var events: List<CalendarEvent> = emptyList(),
    private val onEventClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    // Unique view type constant for this adapter
    override fun getItemViewType(position: Int): Int {
        return EVENT_VIEW_TYPE
    }

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.event_card)
        val title: TextView = itemView.findViewById(R.id.event_title)
        val description: TextView = itemView.findViewById(R.id.event_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        
        // Set event details
        holder.title.text = event.title
        
        // Build description text that includes roll number if accepted
        val descriptionBuilder = StringBuilder()
        
        // Add description
        descriptionBuilder.append(event.description)

        // Add location if available
        if (event.location.isNotBlank()) {
            descriptionBuilder.append("\nLocation: ${event.location}")
        }

        // Add time slot
        descriptionBuilder.append("\nTime: ${event.timeSlot}")

        // Add status
        descriptionBuilder.append("\nStatus: ${event.status}")
        
        // Add roll number information if a class has been accepted - make it more prominent
        if (event.eventType == EventType.TEACHING && event.acceptedByRollNumber.isNotEmpty()) {
            descriptionBuilder.append("\n=================================")
            descriptionBuilder.append("\n✓ CLASS SUBSTITUTED")
            descriptionBuilder.append("\n✓ Accepted by: ${event.acceptedByRollNumber}")
            descriptionBuilder.append("\n=================================")
        }
        
        holder.description.text = descriptionBuilder.toString()
        
        // Set card color based on event type and status
        val cardColor = when {
            event.eventType == EventType.TEACHING && event.status == EventStatus.ACCEPTED -> 
                R.color.slot_booked // Use green for accepted classes
            event.eventType == EventType.TEACHING -> 
                R.color.teaching_day
            else -> 
                R.color.accent
        }
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, cardColor))
        
        // Set click listener
        holder.card.setOnClickListener {
            onEventClick(event)
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<CalendarEvent>) {
        Log.d("EventAdapter", "Updating events: ${newEvents.size}")
        val diffCallback = EventDiffCallback(events, newEvents)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        events = newEvents
        diffResult.dispatchUpdatesTo(this)
    }
    
    private class EventDiffCallback(
        private val oldList: List<CalendarEvent>,
        private val newList: List<CalendarEvent>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.id == newItem.id &&
                   oldItem.title == newItem.title &&
                   oldItem.description == newItem.description &&
                   oldItem.status == newItem.status
        }
    }
    
    companion object {
        // Define a unique view type constant for this adapter
        const val EVENT_VIEW_TYPE = 100
    }
} 