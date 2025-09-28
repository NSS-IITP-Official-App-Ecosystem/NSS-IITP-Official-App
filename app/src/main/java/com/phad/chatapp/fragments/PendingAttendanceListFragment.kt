package com.phad.chatapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.R
import com.phad.chatapp.MainActivity
import com.phad.chatapp.adapters.PendingAttendanceAdapter
import com.phad.chatapp.databinding.FragmentPendingAttendanceListBinding
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.AttendanceViewModelFactory
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.widget.FrameLayout

class PendingAttendanceListFragment : Fragment() {
    private val TAG = "PendingAttendanceList"
    private var _binding: FragmentPendingAttendanceListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AttendanceViewModel
    private lateinit var pendingAttendanceAdapter: PendingAttendanceAdapter
    private val args: PendingAttendanceListFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPendingAttendanceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets to the root view to avoid status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBarsInsets.top)
            insets
        }

        // Hide the bottom navigation and toolbar when this fragment is active
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            it.findViewById<FrameLayout>(R.id.bottom_nav_container)?.visibility = View.GONE
        }

        // Set up RecyclerView
        binding.recyclerPendingAttendance.layoutManager = LinearLayoutManager(context)

        // Initialize adapter with an empty list and set it to RecyclerView
        pendingAttendanceAdapter = PendingAttendanceAdapter(emptyList()) { submission ->
            // Navigate to detail fragment
            findNavController().navigate(
                R.id.action_pendingAttendanceListFragment_to_pendingAttendanceDetailFragment,
                bundleOf("submission" to submission)
            )
        }
        binding.recyclerPendingAttendance.adapter = pendingAttendanceAdapter

        // Initialize ViewModel with factory
        val factory = AttendanceViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[AttendanceViewModel::class.java]

        // Get event name from arguments
        val eventName = args.eventName
        Log.d(TAG, "Event name from arguments: $eventName")

        // Load pending attendance submissions
        viewModel.loadPendingAttendanceSubmissions(eventName)

        // Observe pending submissions
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pendingSubmissions.collectLatest { submissions ->
                if (submissions.isEmpty()) {
                    binding.textNoPending.visibility = View.VISIBLE
                    binding.recyclerPendingAttendance.visibility = View.GONE
                } else {
                    binding.textNoPending.visibility = View.GONE
                    binding.recyclerPendingAttendance.visibility = View.VISIBLE

                    // Update data in the existing adapter
                    pendingAttendanceAdapter.updateSubmissions(submissions)
                }
            }
        }

        // Set up click listener for the back button in the header
        binding.buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        // Show the bottom navigation and toolbar again when the fragment is destroyed
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            it.findViewById<FrameLayout>(R.id.bottom_nav_container)?.visibility = View.VISIBLE
        }
    }
} 