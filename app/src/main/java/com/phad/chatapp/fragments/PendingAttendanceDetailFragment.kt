package com.phad.chatapp.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.phad.chatapp.databinding.FragmentPendingAttendanceDetailBinding
import com.phad.chatapp.models.AttendanceSubmission
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.AttendanceViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import com.phad.chatapp.R

class PendingAttendanceDetailFragment : Fragment() {

    private var _binding: FragmentPendingAttendanceDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AttendanceViewModel by viewModels { 
        AttendanceViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPendingAttendanceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get AttendanceSubmission object from arguments
        val submission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("submission", AttendanceSubmission::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("submission")
        }

        if (submission != null) {
            displaySubmissionDetails(submission)
        } else {
            // TODO: Handle case where submission is missing (e.g., show error or go back)
            showToast("Error: Attendance submission data not found.")
            findNavController().popBackStack()
        }
    }

    private fun displaySubmissionDetails(submission: AttendanceSubmission) {
        Log.d("PendingDetail", "Submission received: $submission") // Log the full submission object
        Log.d("PendingDetail", "Location GeoPoint: ${submission.location}") // Log the location GeoPoint

        binding.rollNumberTextView.text = "Roll Number: ${submission.rollNumber}"
        binding.timestampTextView.text = "Timestamp: ${formatTimestamp(submission.timestamp)}"

        val locationText = formatLocation(submission.location)
        binding.locationTextView.text = locationText
        Log.d("PendingDetail", "Location TextView text set to: $locationText") // Log the text being set

        binding.approvalStatusTextView.text = "Status: ${submission.approvalStatus}"

        // Load image using Glide
        if (submission.imageUrl.isNotEmpty()) {
             Glide.with(this)
                .load(submission.imageUrl)
                .placeholder(R.drawable.placeholder_image) // Add a placeholder if you have one
                .error(R.drawable.error_image) // Add an error image if you have one
                .into(binding.submittedImageView)

            // Make image clickable to view in full screen
            binding.submittedImageView.setOnClickListener {
                val action = PendingAttendanceDetailFragmentDirections.actionPendingAttendanceDetailFragmentToFullScreenImageFragment(submission.imageUrl)
                findNavController().navigate(action)
            }
        } else {
             // Handle case where image URL is empty or null
             binding.submittedImageView.setImageResource(R.drawable.no_image_available) // Set a default image
             binding.submittedImageView.isEnabled = false // Disable click if no image
        }


        // Implement Approve and Reject button click listeners
        binding.approveButton.setOnClickListener {
            lifecycleScope.launch {
                 // Call ViewModel function to approve
                viewModel.approveAttendance(submission)
                 showToast("Attendance approved!")
                 // TODO: Update UI or navigate back after approval
                 findNavController().popBackStack()
            }
        }

        binding.rejectButton.setOnClickListener {
             lifecycleScope.launch {
                 // Call ViewModel function to reject
                viewModel.rejectAttendance(submission)
                 showToast("Attendance rejected!")
                 // TODO: Update UI or navigate back after rejection
                 findNavController().popBackStack()
            }
        }
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
        val date = timestamp.toDate()
        val formatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ss a", Locale.getDefault())
        return formatter.format(date)
    }

    private fun formatLocation(location: com.google.firebase.firestore.GeoPoint?): String {
        return if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            "Lat: ${String.format("%.4f", lat)}, Long: ${String.format("%.4f", lng)}"
        } else {
            "Location not available"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 