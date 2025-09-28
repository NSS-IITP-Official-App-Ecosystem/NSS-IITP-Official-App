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

class AdminAttendanceApprovalFragment : Fragment(), AttendanceApprovalAdapter.AttendanceApprovalListener {
    private val TAG = "AdminAttendanceApproval"
    
    // UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView
    
    // Adapter
    private lateinit var adapter: AttendanceApprovalAdapter
    private val attendanceItems = mutableListOf<AttendanceApprovalItem>()
    
    // Repositories and services
    private lateinit var attendanceRepository: AttendanceRepository
    private lateinit var userRepository: UserRepository
    private lateinit var attendanceService: AttendanceService
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_attendance_approval, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize repositories and services
        attendanceRepository = AttendanceRepository()
        userRepository = UserRepository()
        val cloudinaryService = context?.let { CloudinaryService(it) } 
            ?: throw IllegalStateException("Context is null")
        attendanceService = AttendanceService(
            attendanceRepository,
            userRepository,
            cloudinaryService
        )
        
        // Initialize UI components
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyView = view.findViewById(R.id.emptyView)
        
        // Setup toolbar
        view.findViewById<View>(R.id.backButton).setOnClickListener {
            requireActivity().onBackPressed()
        }
        
        // Setup RecyclerView
        adapter = AttendanceApprovalAdapter(attendanceItems, this)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // Load pending attendances
        loadPendingAttendances()
    }
    
    private fun loadPendingAttendances() {
        showLoading(true)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = attendanceService.getPendingAttendances()
                
                if (result.isSuccess) {
                    val attendances = result.getOrNull() ?: emptyList()
                    
                    if (attendances.isEmpty()) {
                        showEmptyView(true)
                    } else {
                        showEmptyView(false)
                        fetchUserDetailsForAttendances(attendances)
                    }
                } else {
                    showEmptyView(true)
                    Toast.makeText(
                        context,
                        "Failed to load attendance requests: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pending attendances", e)
                showEmptyView(true)
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private suspend fun fetchUserDetailsForAttendances(attendances: List<Attendance>) {
        attendanceItems.clear()
        
        for (attendance in attendances) {
            try {
                val userResult = userRepository.getUserByRollNumber(attendance.rollNumber)
                
                if (userResult.isSuccess) {
                    val user = userResult.getOrNull()
                    
                    if (user != null) {
                        attendanceItems.add(
                            AttendanceApprovalItem(
                                id = attendance.id,
                                rollNumber = attendance.rollNumber,
                                name = user.name,
                                timestamp = attendance.timestamp.toDate(),
                                location = attendance.location,
                                submittedImageUrl = attendance.imageUrl,
                                referenceImageUrl = user.referenceImageUrl
                            )
                        )
                    } else {
                        // User not found, but still add the attendance item with available info
                        attendanceItems.add(
                            AttendanceApprovalItem(
                                id = attendance.id,
                                rollNumber = attendance.rollNumber,
                                name = "Unknown User",
                                timestamp = attendance.timestamp.toDate(),
                                location = attendance.location,
                                submittedImageUrl = attendance.imageUrl,
                                referenceImageUrl = null
                            )
                        )
                    }
                } else {
                    Log.e(TAG, "Error fetching user details", userResult.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing attendance item", e)
            }
        }
        
        // Sort by timestamp (newest first)
        attendanceItems.sortByDescending { it.timestamp }
        
        // Update the UI
        updateUI()
    }
    
    private fun updateUI() {
        adapter.notifyDataSetChanged()
        showEmptyView(attendanceItems.isEmpty())
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
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
                    
                    // Remove the item from the list
                    val position = attendanceItems.indexOf(item)
                    if (position != -1) {
                        attendanceItems.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        showEmptyView(attendanceItems.isEmpty())
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
                    
                    // Remove the item from the list
                    val position = attendanceItems.indexOf(item)
                    if (position != -1) {
                        attendanceItems.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        showEmptyView(attendanceItems.isEmpty())
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
    
    private fun formatTimestamp(date: Date): String {
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
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