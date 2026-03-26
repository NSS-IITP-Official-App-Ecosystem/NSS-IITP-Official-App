package com.phad.chatapp.features.calendar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.features.calendar.models.EventStatus
import com.phad.chatapp.features.calendar.models.LeaveApplication
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log

class LeaveApplicationAdapter(
    private val userRollNumber: String = "",
    private var leaveApplications: List<LeaveApplication> = emptyList(),
    private var isAdmin: Boolean = false,
    private val onLeaveClick: (LeaveApplication) -> Unit
) : RecyclerView.Adapter<LeaveApplicationAdapter.LeaveViewHolder>() {

    fun setAdminStatus(admin: Boolean) {
        if (isAdmin != admin) {
            isAdmin = admin
            notifyDataSetChanged()
        }
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun getItemViewType(position: Int): Int = LEAVE_VIEW_TYPE

    class LeaveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.event_card)
        val accentBar: View = itemView.findViewById(R.id.view_event_accent)
        val title: TextView = itemView.findViewById(R.id.event_title)
        val statusBadge: TextView = itemView.findViewById(R.id.tv_event_status_badge)
        val time: TextView = itemView.findViewById(R.id.tv_event_time)
        val schoolClass: TextView = itemView.findViewById(R.id.tv_event_school_class)
        val acceptorInfo: TextView = itemView.findViewById(R.id.tv_event_acceptor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return LeaveViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaveViewHolder, position: Int) {
        val leave = leaveApplications[position]
        val ctx = holder.itemView.context
        val isMyLeave = leave.rollNumber == userRollNumber

        // Title / subject line
        holder.title.text = leave.subject

        // Time slot with better icon
        holder.time.text = "🕐  ${leave.slot}"

        // School
        holder.schoolClass.text = "🏛  ${leave.school}"

        // Status badge + accent bar + faint card tint
        val accentColor: Int
        when {
            leave.status == EventStatus.ACCEPTED && leave.substitutedByRollNumber.isNotEmpty() -> {
                val coveredByText = if (leave.substitutedByName.isNotBlank()) {
                    "${leave.substitutedByName} (${leave.substitutedByRollNumber})"
                } else {
                    leave.substitutedByRollNumber
                }
                
                if (isAdmin) {
                    holder.statusBadge.text = "Covered"
                    accentColor = ContextCompat.getColor(ctx, R.color.cal_success)
                    holder.acceptorInfo.visibility = View.VISIBLE
                    holder.acceptorInfo.text = "✔  Covered by $coveredByText"
                } else {
                    holder.statusBadge.text = if (isMyLeave) "Class Covered" else "Substituting"
                    accentColor = if (isMyLeave)
                        ContextCompat.getColor(ctx, R.color.cal_accent)
                    else
                        ContextCompat.getColor(ctx, R.color.cal_success)
                    holder.acceptorInfo.visibility = View.VISIBLE
                    holder.acceptorInfo.text = if (isMyLeave)
                        "✔  Covered by $coveredByText"
                    else
                        "✔  Substituting for ${leave.userName}"
                }
            }
            leave.status == EventStatus.APPROVED -> {
                if (isAdmin) {
                    holder.statusBadge.text = "Open for Sub"
                    accentColor = ContextCompat.getColor(ctx, R.color.cal_substitution_open)
                } else {
                    holder.statusBadge.text = if (isMyLeave) "Your Leave" else "Open for Sub"
                    val colorResId = if (isMyLeave) R.color.cal_error else R.color.cal_substitution_open
                    accentColor = ContextCompat.getColor(ctx, colorResId)
                }
                holder.acceptorInfo.visibility = View.GONE
            }
            leave.status == EventStatus.REJECTED -> {
                holder.statusBadge.text = "Rejected"
                accentColor = ContextCompat.getColor(ctx, R.color.cal_text_disabled)
                holder.acceptorInfo.visibility = View.GONE
            }
            else -> {
                holder.statusBadge.text = "Pending"
                accentColor = ContextCompat.getColor(ctx, R.color.cal_warning)
                holder.acceptorInfo.visibility = View.GONE
            }
        }
        holder.accentBar.setBackgroundColor(accentColor)
        // Faint tinted card background (~12% opacity of accent)
        val faintColor = android.graphics.Color.argb(
            0x1E,
            android.graphics.Color.red(accentColor),
            android.graphics.Color.green(accentColor),
            android.graphics.Color.blue(accentColor)
        )
        holder.card.setBackgroundColor(faintColor)

        // Click listener
        holder.card.setOnClickListener { onLeaveClick(leave) }
    }

    override fun getItemCount(): Int = leaveApplications.size

    fun updateLeaves(newLeaves: List<LeaveApplication>) {
        Log.d("LeaveAdapter", "Updating leaves: ${newLeaves.size}")
        val diffCallback = LeaveDiffCallback(leaveApplications, newLeaves)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        leaveApplications = newLeaves
        diffResult.dispatchUpdatesTo(this)
    }

    private class LeaveDiffCallback(
        private val oldList: List<LeaveApplication>,
        private val newList: List<LeaveApplication>
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
                   oldItem.subject == newItem.subject &&
                   oldItem.userName == newItem.userName &&
                   oldItem.status == newItem.status
        }
    }

    companion object {
        const val LEAVE_VIEW_TYPE = 200
    }
}
