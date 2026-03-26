package com.phad.chatapp.features.calendar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.features.calendar.models.CalendarEvent
import com.phad.chatapp.features.calendar.models.EventType
import com.phad.chatapp.features.calendar.models.EventStatus
import android.util.Log

class EventAdapter(
    private var events: List<CalendarEvent> = emptyList(),
    private val onEventClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    override fun getItemViewType(position: Int): Int = EVENT_VIEW_TYPE

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.event_card)
        val accentBar: View = itemView.findViewById(R.id.view_event_accent)
        val title: TextView = itemView.findViewById(R.id.event_title)
        val statusBadge: TextView = itemView.findViewById(R.id.tv_event_status_badge)
        val time: TextView = itemView.findViewById(R.id.tv_event_time)
        val schoolClass: TextView = itemView.findViewById(R.id.tv_event_school_class)
        val acceptorInfo: TextView = itemView.findViewById(R.id.tv_event_acceptor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val ctx = holder.itemView.context

        // Subject name
        holder.title.text = event.title

        // Time slot with better icon
        holder.time.text = "🕐  ${event.timeSlot}"

        // School · Class from description
        holder.schoolClass.text = "🏛  ${event.description}"

        // Status badge text + accent bar color + faint card tint
        val accentColorRes: Int
        when (event.status) {
            EventStatus.SCHEDULED -> {
                holder.statusBadge.text = "Scheduled"
                accentColorRes = R.color.cal_info // blue
            }
            EventStatus.ACCEPTED -> {
                holder.statusBadge.text = "Class Covered"
                accentColorRes = R.color.cal_success // green
            }
            EventStatus.PENDING -> {
                holder.statusBadge.text = "Leave Applied"
                accentColorRes = R.color.cal_accent // yellow
            }
            EventStatus.APPROVED -> {
                holder.statusBadge.text = "Leave Active"
                accentColorRes = R.color.cal_success
            }
            EventStatus.REJECTED -> {
                holder.statusBadge.text = "Rejected"
                accentColorRes = R.color.cal_error // red
            }
            else -> {
                holder.statusBadge.text = event.status.name
                accentColorRes = R.color.cal_text_secondary
            }
        }
        val accentColor = ContextCompat.getColor(ctx, accentColorRes)
        holder.accentBar.setBackgroundColor(accentColor)
        // Set faint tinted card background (~12% opacity of accent color)
        val faintColor = android.graphics.Color.argb(
            0x1E,
            android.graphics.Color.red(accentColor),
            android.graphics.Color.green(accentColor),
            android.graphics.Color.blue(accentColor)
        )
        holder.card.setBackgroundColor(faintColor)

        // Acceptor info – only visible when someone has accepted the class
        if (event.acceptedByRollNumber.isNotEmpty()) {
            holder.acceptorInfo.visibility = View.VISIBLE
            val acceptorNameText = if (event.bookedByName.isNotBlank()) {
                "${event.bookedByName} (${event.acceptedByRollNumber})"
            } else {
                event.acceptedByRollNumber
            }
            holder.acceptorInfo.text = "✔  Covered by $acceptorNameText"
        } else {
            holder.acceptorInfo.visibility = View.GONE
        }

        // Click listener
        holder.card.setOnClickListener { onEventClick(event) }
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
                   oldItem.status == newItem.status &&
                   oldItem.acceptedByRollNumber == newItem.acceptedByRollNumber
        }
    }

    companion object {
        const val EVENT_VIEW_TYPE = 100
    }
}
