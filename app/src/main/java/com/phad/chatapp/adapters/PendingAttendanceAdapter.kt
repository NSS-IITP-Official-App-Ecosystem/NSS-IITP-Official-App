package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.models.AttendanceSubmission
import java.text.SimpleDateFormat
import java.util.Locale

class PendingAttendanceAdapter(
    private var submissions: List<AttendanceSubmission>,
    private val onItemClick: (AttendanceSubmission) -> Unit
) : RecyclerView.Adapter<PendingAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.text_name)
        val textRollNumber: TextView = view.findViewById(R.id.text_roll_number)
        val textTimestamp: TextView = view.findViewById(R.id.text_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_attendance_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val submission = submissions[position]
        
        holder.textName.text = submission.studentName
        holder.textRollNumber.text = submission.studentRollNumber
        
        // Format timestamp
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(submission.timestamp.toDate())
        holder.textTimestamp.text = formattedDate
        
        holder.itemView.setOnClickListener {
            onItemClick(submission)
        }
    }

    override fun getItemCount() = submissions.size

    // Function to update the list of submissions
    fun updateSubmissions(newSubmissions: List<AttendanceSubmission>) {
        submissions = newSubmissions
        notifyDataSetChanged()
    }
} 