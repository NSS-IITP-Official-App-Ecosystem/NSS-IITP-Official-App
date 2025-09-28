package com.phad.chatapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phad.chatapp.R
import com.phad.chatapp.adapters.AttendanceApprovalAdapter
import com.phad.chatapp.models.Attendance
import com.phad.chatapp.models.AttendanceApprovalItem
import com.phad.chatapp.repositories.AttendanceRepository
import com.phad.chatapp.repositories.UserRepository
import com.phad.chatapp.services.AttendanceService
import com.phad.chatapp.services.CloudinaryService
import kotlinx.coroutines.launch
import java.util.*

class NssAdminAttendanceApprovalFragment : Fragment(), AttendanceApprovalAdapter.AttendanceApprovalListener {
    override fun onApproveClicked(item: AttendanceApprovalItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Approve Attendance")
            .setMessage("Are you sure you want to approve this attendance request for ${item.name} (${item.rollNumber})?")
            .setPositiveButton("Approve") { _, _ ->
                approveAttendance(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRejectClicked(item: AttendanceApprovalItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject Attendance")
            .setMessage("Are you sure you want to reject this attendance request for ${item.name} (${item.rollNumber})?")
            .setPositiveButton("Reject") { _, _ ->
                rejectAttendance(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewDetailsClicked(item: AttendanceApprovalItem) {
        // Create and show the details dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_attendance_details, null)

        // Set dialog data
        dialogView.findViewById<TextView>(R.id.nameTextView).text = item.name
        dialogView.findViewById<TextView>(R.id.rollNumberTextView).text = item.rollNumber
        dialogView.findViewById<TextView>(R.id.timestampTextView).text = formatTimestamp(item.timestamp)
        dialogView.findViewById<TextView>(R.id.locationTextView).text = formatLocation(item.location)

        // Load images
        val submittedImageView = dialogView.findViewById<ImageView>(R.id.submittedImageView)
        val referenceImageView = dialogView.findViewById<ImageView>(R.id.referenceImageView)

        item.submittedImageUrl?.let {
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.ic_person)
                .into(submittedImageView)
        }

        item.referenceImageUrl?.let {
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.ic_person)
                .into(referenceImageView)
        }

        // Show dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        // Set button listeners
        dialogView.findViewById<Button>(R.id.approveButton).setOnClickListener {
            approveAttendance(item)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.rejectButton).setOnClickListener {
            rejectAttendance(item)
            dialog.dismiss()
        }

        dialog.show()
    }

    private val TAG = "NssAdminAttendanceApproval"
    private lateinit var attendanceService: AttendanceService
    private val attendanceItems = mutableListOf<AttendanceApprovalItem>()
    private lateinit var adapter: AttendanceApprovalAdapter

    private fun approveAttendance(item: AttendanceApprovalItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = attendanceService.approveAttendance(item.id)
                if (result.isSuccess) {
                    Toast.makeText(
                        context,
                        "Attendance approved successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    val position = attendanceItems.indexOf(item)
                    if (position != -1) {
                        attendanceItems.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        // Optionally show empty view if needed
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Failed to approve attendance: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error approving attendance", e)
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun rejectAttendance(item: AttendanceApprovalItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = attendanceService.rejectAttendance(item.id)
                if (result.isSuccess) {
                    Toast.makeText(
                        context,
                        "Attendance rejected successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    val position = attendanceItems.indexOf(item)
                    if (position != -1) {
                        attendanceItems.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        // Optionally show empty view if needed
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Failed to reject attendance: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting attendance", e)
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun formatTimestamp(date: java.util.Date): String {
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }

    private fun formatLocation(location: com.google.firebase.firestore.GeoPoint?): String {
        return if (location != null) {
            "Lat: ${String.format("%.4f", location.latitude)}, Long: ${String.format("%.4f", location.longitude)}"
        } else {
            "Location not available"
        }
    }
} 