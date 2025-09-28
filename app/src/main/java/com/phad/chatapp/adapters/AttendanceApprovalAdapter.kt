package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.phad.chatapp.R
import com.phad.chatapp.models.AttendanceApprovalItem
import java.text.SimpleDateFormat
import java.util.*

class AttendanceApprovalAdapter(
    private val items: List<AttendanceApprovalItem>,
    private val listener: AttendanceApprovalListener
) : RecyclerView.Adapter<AttendanceApprovalAdapter.ViewHolder>() {

    interface AttendanceApprovalListener {
        fun onApproveClicked(item: AttendanceApprovalItem)
        fun onRejectClicked(item: AttendanceApprovalItem)
        fun onViewDetailsClicked(item: AttendanceApprovalItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_approval, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val rollNumberTextView: TextView = itemView.findViewById(R.id.rollNumberTextView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        private val submittedImageView: ImageView = itemView.findViewById(R.id.submittedImageView)
        private val viewDetailsButton: Button = itemView.findViewById(R.id.viewDetailsButton)
        private val approveButton: Button = itemView.findViewById(R.id.approveButton)
        private val rejectButton: Button = itemView.findViewById(R.id.rejectButton)

        fun bind(item: AttendanceApprovalItem) {
            nameTextView.text = item.name
            rollNumberTextView.text = item.rollNumber
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
            timestampTextView.text = dateFormat.format(item.timestamp)
            
            // Load image with Glide
            item.submittedImageUrl?.let { url ->
                Glide.with(itemView.context)
                    .load(url)
                    .placeholder(R.drawable.ic_person)
                    .into(submittedImageView)
            }
            
            // Set click listeners
            viewDetailsButton.setOnClickListener {
                listener.onViewDetailsClicked(item)
            }
            
            approveButton.setOnClickListener {
                listener.onApproveClicked(item)
            }
            
            rejectButton.setOnClickListener {
                listener.onRejectClicked(item)
            }
            
            // Make the whole item clickable
            itemView.setOnClickListener {
                listener.onViewDetailsClicked(item)
            }
        }
    }
} 