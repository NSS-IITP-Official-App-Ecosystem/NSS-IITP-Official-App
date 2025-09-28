package com.phad.chatapp.fragments

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.phad.chatapp.R
import com.phad.chatapp.adapters.LiveEventsAdapter
import com.phad.chatapp.databinding.DialogCreateEventBinding
import com.phad.chatapp.databinding.DialogPendingAttendanceBinding
import com.phad.chatapp.databinding.FragmentAttendanceManagerBinding
import com.phad.chatapp.databinding.ItemLiveEventBinding
import com.phad.chatapp.databinding.ItemPendingAttendanceBinding
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.models.AttendanceSubmission
import com.phad.chatapp.models.User
import com.phad.chatapp.utils.AttendanceEventUtils
import com.phad.chatapp.utils.CloudinaryManager
import com.phad.chatapp.utils.PermissionManager
import com.phad.chatapp.viewmodels.AttendanceViewModel
import com.phad.chatapp.viewmodels.AttendanceViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.utils.AttendanceUtils
import com.phad.chatapp.utils.AttendanceStatsUpdater

class AttendanceManagerFragment : Fragment() {
    private var _binding: FragmentAttendanceManagerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AttendanceViewModel by viewModels { 
        AttendanceViewModelFactory(requireActivity().application)
    }
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var currentLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentUser: User? = null
    private var selectedEvent: AttendanceEvent? = null
    private var isAdmin: Boolean = false
    private var adminType: String = ""
    private var capturedImageUri: Uri? = null
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var cameraSelector: CameraSelector

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.CAMERA] == true -> {
                setupCamera()
            }
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                getCurrentLocation()
            }
            else -> {
                Snackbar.make(
                    binding.root,
                    "Camera and location permissions are required",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupClickListeners()
        checkUserType()
        // Initialize CloudinaryManager
        CloudinaryManager.init(requireContext().applicationContext)

        // Initially disable mark attendance button
        binding.markAttendanceButton.isEnabled = false
    }

    private fun setupUI() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        // Initialize camera components
        preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.cameraButton.setOnClickListener {
            if (checkPermissions()) {
                setupCamera()
            } else {
                requestPermissions()
            }
        }

        binding.captureButton.setOnClickListener {
            captureImage()
        }

        binding.switchCameraButton.setOnClickListener {
            // Switch camera logic will be implemented
        }

        binding.getLocationButton.setOnClickListener {
            fetchAndStoreLocation()
        }

        binding.markAttendanceButton.setOnClickListener {
            submitAttendance()
        }

        // Admin specific click listeners
        binding.createEventFab?.setOnClickListener {
            showCreateEventDialog()
        }
    }

    private fun checkUserType() {
        lifecycleScope.launch {
            try {
                Log.d("AttendanceManager", "Checking user type...")
                val user = viewModel.getCurrentUser()
                if (user != null) {
                    Log.d("AttendanceManager", "User fetched: ${user.rollNumber}, Type: ${user.userType}")
                    this@AttendanceManagerFragment.currentUser = user
                    
                    // Determine isAdmin and adminType based on user.userType
                    isAdmin = user.userType == "Admin" || user.userType == "Admin1" || user.userType == "Admin2"
                    adminType = if (isAdmin) user.userType else ""
                    
                    Log.d("AttendanceManager", "isAdmin: $isAdmin, adminType: $adminType")

                    if (isAdmin) {
                        showAdminView()
                        loadLiveEvents()
                    } else {
                        showStudentView()
                        loadLiveEventsForStudent()
                    }
                } else {
                    Log.e("AttendanceManager", "getCurrentUser() returned null.")
                    Toast.makeText(requireContext(), "Could not fetch user data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error checking user type", e)
                Toast.makeText(requireContext(), "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAdminView() {
        binding.adminView?.visibility = View.VISIBLE
        binding.studentView?.visibility = View.GONE
    }

    private fun showStudentView() {
        binding.adminView?.visibility = View.GONE
        binding.studentView?.visibility = View.VISIBLE
    }

    private fun loadLiveEvents() {
        lifecycleScope.launch {
            try {
                // Check if view is still available
                if (_binding == null) return@launch
                binding.adminProgressBar?.visibility = View.VISIBLE
                val events = viewModel.getLiveEvents()
                // Check if view is still available
                if (_binding == null) return@launch
                if (events.isEmpty()) {
                    binding.noEventsTextView?.visibility = View.VISIBLE
                    binding.liveEventsRecyclerView?.visibility = View.GONE
                } else {
                    binding.noEventsTextView?.visibility = View.GONE
                    binding.liveEventsRecyclerView?.visibility = View.VISIBLE
                    // Set up RecyclerView adapter
                    setupLiveEventsAdapter(events)
                }
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error loading live events", e)
                // Check if context is still available before showing Toast
                if (context != null) {
                    Toast.makeText(requireContext(), "Error loading events", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Check if view is still available
                if (_binding == null) return@launch
                binding.adminProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun loadLiveEventsForStudent() {
        lifecycleScope.launch {
            try {
                // Check if view is still available
                if (_binding == null) return@launch
                val events = viewModel.getLiveEvents()
                // Check if view is still available
                if (_binding == null) return@launch
                val eventNames = events.map { it.getEventName() }
                setupEventSpinner(eventNames)
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error loading live events", e)
                // Check if context is still available before showing Toast
                if (context != null) {
                    Toast.makeText(requireContext(), "Error loading events", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupEventSpinner(eventNames: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, eventNames)
        binding.eventSelectionSpinner?.setAdapter(adapter)
        binding.eventSelectionSpinner?.setOnItemClickListener { _, _, position, _ ->
            lifecycleScope.launch {
                val events = viewModel.getLiveEvents()
                if (position < events.size) {
                    selectedEvent = events[position]
                }
            }
        }
    }

    private fun showCreateEventDialog() {
        val dialogBinding = DialogCreateEventBinding.inflate(layoutInflater)

        // Set up date picker
        var selectedDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        dialogBinding.eventDateInput.setText(dateFormat.format(selectedDate.time))

        // Set up time pickers with default times (time components only)
        var openingTime = AttendanceEventUtils.createTimeFromHourMinute(Date(), 9, 0) // 9:00 AM
        var closingTime = AttendanceEventUtils.createTimeFromHourMinute(Date(), 17, 0) // 5:00 PM

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        dialogBinding.openingTimeInput.setText(timeFormat.format(openingTime))
        dialogBinding.closingTimeInput.setText(timeFormat.format(closingTime))

        // Set default hours value
        dialogBinding.hoursInput.setText("0")

        dialogBinding.eventDateInput.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate.set(year, month, dayOfMonth)
                    dialogBinding.eventDateInput.setText(dateFormat.format(selectedDate.time))

                    // Time objects don't need to be updated when date changes
                    // since we now store date and time separately
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        // Opening time picker
        dialogBinding.openingTimeInput.setOnClickListener {
            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    openingTime = AttendanceEventUtils.createTimeFromHourMinute(Date(), hourOfDay, minute)
                    dialogBinding.openingTimeInput.setText(timeFormat.format(openingTime))
                },
                AttendanceEventUtils.getHourFromDate(openingTime),
                AttendanceEventUtils.getMinuteFromDate(openingTime),
                false // Use 12-hour format
            )
            timePicker.show()
        }

        // Closing time picker
        dialogBinding.closingTimeInput.setOnClickListener {
            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    closingTime = AttendanceEventUtils.createTimeFromHourMinute(Date(), hourOfDay, minute)
                    dialogBinding.closingTimeInput.setText(timeFormat.format(closingTime))
                },
                AttendanceEventUtils.getHourFromDate(closingTime),
                AttendanceEventUtils.getMinuteFromDate(closingTime),
                false // Use 12-hour format
            )
            timePicker.show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { dialog, _ ->
                val eventName = dialogBinding.eventNameInput.text.toString()
                val description = dialogBinding.descriptionInput.text.toString()
                val hoursText = dialogBinding.hoursInput.text.toString()
                val hours = hoursText.toIntOrNull() ?: -1

                when {
                    eventName.isBlank() -> {
                        Toast.makeText(requireContext(), "Event name is required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    hoursText.isBlank() || hours < 0 -> {
                        Toast.makeText(requireContext(), "Please enter valid hours (0 or greater)", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    !AttendanceEventUtils.validateEventTimes(openingTime, closingTime) -> {
                        Toast.makeText(requireContext(), "Closing time must be after opening time", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    !AttendanceEventUtils.validateOpeningTimeNotInPast(selectedDate.time, openingTime) -> {
                        Toast.makeText(requireContext(), "Opening time cannot be in the past", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    else -> {
                        createAttendanceEvent(eventName, description, selectedDate.time, openingTime, closingTime, hours)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createAttendanceEvent(name: String, description: String, eventDate: Date, openingTime: Date, closingTime: Date, hours: Int) {
        lifecycleScope.launch {
            try {
                // Generate document ID using the new format
                val documentId = AttendanceEventUtils.generateDocumentId(eventDate, name)

                // Create new date/time format data
                val (dateString, timeRangeString) = AttendanceEventUtils.createNewFormatEventTimeData(eventDate, openingTime, closingTime)

                Log.d("AttendanceManager", "Creating event with:")
                Log.d("AttendanceManager", "  Date string: $dateString")
                Log.d("AttendanceManager", "  Time range: $timeRangeString")

                val event = AttendanceEvent(
                    id = documentId,
                    eventDate = dateString,
                    eventTime = timeRangeString,
                    hours = hours,
                    description = description,
                    createdBy = currentUser?.rollNumber ?: "",
                    creatorName = currentUser?.name ?: "",
                    createdAt = Timestamp.now(),
                    attendees = emptyList(),
                    closedAt = null,
                    _isLive = true // Explicitly set to true for new events
                )
                viewModel.createAttendanceEvent(event)
                AttendanceUtils.incrementTotalEvents()
                loadLiveEvents()
                Toast.makeText(requireContext(), "Event created successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error creating event", e)
                Toast.makeText(requireContext(), "Error creating event", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPendingAttendanceDialog(event: AttendanceEvent) {
        val dialogBinding = DialogPendingAttendanceBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .show()

        lifecycleScope.launch {
            try {
                dialogBinding.progressBar.visibility = View.VISIBLE
                val pendingSubmissions = viewModel.getPendingSubmissions(event.getEventName())
                if (pendingSubmissions.isEmpty()) {
                    dialogBinding.noPendingTextView.visibility = View.VISIBLE
                    dialogBinding.pendingAttendanceRecyclerView.visibility = View.GONE
                } else {
                    dialogBinding.noPendingTextView.visibility = View.GONE
                    dialogBinding.pendingAttendanceRecyclerView.visibility = View.VISIBLE
                    setupPendingAttendanceAdapter(dialogBinding.pendingAttendanceRecyclerView, pendingSubmissions)
                }
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error loading pending submissions", e)
                Toast.makeText(requireContext(), "Error loading submissions", Toast.LENGTH_SHORT).show()
            } finally {
                dialogBinding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupPendingAttendanceAdapter(
        recyclerView: RecyclerView,
        submissions: List<AttendanceSubmission>
    ) {
        Log.d("AttendanceManager", "setupPendingAttendanceAdapter called with ${submissions.size} submissions")
        // Implementation of RecyclerView adapter for pending submissions
    }

    private fun approveAttendance(submission: AttendanceSubmission) {
        lifecycleScope.launch {
            try {
                viewModel.approveAttendance(submission)
                Toast.makeText(requireContext(), "Attendance approved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error approving attendance", e)
                Toast.makeText(requireContext(), "Error approving attendance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectAttendance(submission: AttendanceSubmission) {
        lifecycleScope.launch {
            try {
                viewModel.rejectAttendance(submission)
                Toast.makeText(requireContext(), "Attendance rejected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error rejecting attendance", e)
                Toast.makeText(requireContext(), "Error rejecting attendance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeEvent(event: AttendanceEvent) {
        lifecycleScope.launch {
            try {
                viewModel.closeEvent(event)
                loadLiveEvents()
                Toast.makeText(requireContext(), "Event closed successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error closing event", e)
                Toast.makeText(requireContext(), "Error closing event", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Clean up duplicate "live" fields from all events in the database
     * This is a one-time cleanup function to fix existing events
     *
     * To run this cleanup, temporarily call this method from onViewCreated() or add a button
     * Example: cleanupDuplicateLiveFields()
     */
    private fun cleanupDuplicateLiveFields() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Starting database cleanup...", Toast.LENGTH_SHORT).show()
                viewModel.cleanupDuplicateLiveFields()
                Toast.makeText(requireContext(), "Database cleanup completed successfully", Toast.LENGTH_LONG).show()
                loadLiveEvents() // Refresh the list after cleanup
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error during database cleanup", e)
                Toast.makeText(requireContext(), "Error during database cleanup", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return PermissionManager.hasRequiredPermissions(requireContext())
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(PermissionManager.REQUIRED_PERMISSIONS)
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                // Get camera provider
                cameraProvider = cameraProviderFuture.get()

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Set up the preview use case
                preview = Preview.Builder()
                    .setTargetRotation(binding.cameraPreviewView.display.rotation)
                    .build()

                // Set up the image capture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(binding.cameraPreviewView.display.rotation)
                    .build()

                // Choose the camera by requiring a lens facing
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    // Set up the preview use case
                    preview.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)

                    // Show camera UI and hide other buttons
                    binding.cameraPreviewView.visibility = View.VISIBLE
                    binding.imageView.visibility = View.GONE
                    binding.markAttendanceButton.visibility = View.GONE
                    binding.cameraButton.visibility = View.GONE
                    binding.captureButton.visibility = View.VISIBLE
                    binding.switchCameraButton.visibility = View.VISIBLE

                } catch (e: Exception) {
                    Log.e("AttendanceManager", "Use case binding failed", e)
                    showToast("Failed to start camera: ${e.message}")
                    // Ensure correct button visibility on failure
                    binding.cameraButton.visibility = View.VISIBLE
                    binding.captureButton.visibility = View.GONE
                    binding.switchCameraButton.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("AttendanceManager", "Camera provider initialization failed", e)
                showToast("Failed to initialize camera: ${e.message}")
                // Ensure correct button visibility on failure
                binding.cameraButton.visibility = View.VISIBLE
                binding.captureButton.visibility = View.GONE
                binding.switchCameraButton.visibility = View.GONE
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        // Create timestamped name and MediaStore entry
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Attendance")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.EMPTY
                    Log.d("AttendanceManager", "Photo capture succeeded: $savedUri")
                    
                    // Stop camera after capturing
                    stopCamera()
                    
                    // Show the captured image
                    binding.cameraPreviewView.visibility = View.GONE
                    binding.imageView.visibility = View.VISIBLE
                    binding.imageView.setImageURI(savedUri)
                    
                    // Show mark attendance button
                    binding.markAttendanceButton.visibility = View.VISIBLE
                    
                    // Store the image URI for later use
                    capturedImageUri = savedUri
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("AttendanceManager", "Photo capture failed: ${exc.message}", exc)
                    showToast("Failed to capture photo: ${exc.message}")
                }
            }
        )
    }

    private fun stopCamera() {
        try {
            cameraProvider.unbindAll()
            binding.cameraPreviewView.visibility = View.GONE
            // Hide capture and switch buttons, show take photo button
            binding.captureButton.visibility = View.GONE
            binding.switchCameraButton.visibility = View.GONE
            binding.cameraButton.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("AttendanceManager", "Error stopping camera", e)
        }
    }

    private fun fetchAndStoreLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            showLocationPermissionDialog()
        }
    }

    private fun getCurrentLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                binding.studentProgressBar.visibility = View.VISIBLE
                binding.getLocationButton.isEnabled = false // Disable Get Location button

                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            currentLocation = location
                            Log.d("AttendanceManager", "Location obtained: ${location.latitude}, ${location.longitude}")
                            showToast("Location obtained!")
                            // Location obtained, enable mark attendance button
                            binding.markAttendanceButton.isEnabled = true
                            binding.getLocationButton.isEnabled = true // Re-enable Get Location button
                        } else {
                            Log.e("AttendanceManager", "Location is null")
                            showToast("Unable to get location. Please try again.")
                            binding.studentProgressBar.visibility = View.GONE
                            binding.getLocationButton.isEnabled = true // Re-enable Get Location button
                        }
                        binding.studentProgressBar.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        Log.e("AttendanceManager", "Error getting location", e)
                        showToast("Error getting location: ${e.message}")
                        binding.studentProgressBar.visibility = View.GONE
                        binding.getLocationButton.isEnabled = true // Re-enable Get Location button
                    }
            } else {
                showToast("Location permission not granted")
            }
        } catch (e: Exception) {
            Log.e("AttendanceManager", "Error in getCurrentLocation", e)
            showToast("Error getting location: ${e.message}")
            binding.studentProgressBar.visibility = View.GONE
            binding.getLocationButton.isEnabled = true // Re-enable Get Location button
        }
    }

    private fun showLocationPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to mark attendance. Please grant location permission.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                showToast("Location permission is required to mark attendance")
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, get location and submit attendance
                    getCurrentLocation()
                } else {
                    // Permission denied
                    showToast("Location permission is required to mark attendance")
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun submitAttendance() {
        // Disable the mark attendance button immediately
        binding.markAttendanceButton.isEnabled = false
        binding.studentProgressBar.visibility = View.VISIBLE

        val rollNumber = binding.rollNumberInput.text.toString()
        if (rollNumber.isBlank()) {
            Toast.makeText(requireContext(), "Please enter your roll number", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        // Add roll number validation check
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User data not available. Please try again.", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        // Check if submitted roll number matches user's roll number (case-insensitive)
        if (!rollNumber.equals(currentUser!!.rollNumber, ignoreCase = true)) {
            Toast.makeText(requireContext(), "Roll number mismatch. Please enter your correct roll number.", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        if (selectedEvent == null) {
            Toast.makeText(requireContext(), "Please select an event", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        if (currentLocation == null) {
            // This should ideally not happen if the button is disabled until location is fetched
            Toast.makeText(requireContext(), "Location not available. Please get location first.", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        // Ensure an image is captured
        val imageUri = capturedImageUri
        if (imageUri == null || imageUri == Uri.EMPTY) {
            Toast.makeText(requireContext(), "Please capture a photo first.", Toast.LENGTH_SHORT).show()
            binding.studentProgressBar.visibility = View.GONE
            binding.markAttendanceButton.isEnabled = true
            return
        }

        lifecycleScope.launch {
            try {
                val imageUrl = CloudinaryManager.uploadImage(imageUri)

                val submission = AttendanceSubmission(
                    rollNumber = rollNumber,
                    studentName = currentUser!!.name,
                    studentRollNumber = currentUser!!.rollNumber,
                    eventName = selectedEvent!!.getEventName(),
                    timestamp = Timestamp.now(),
                    location = GeoPoint(currentLocation!!.latitude, currentLocation!!.longitude),
                    imageUrl = imageUrl,
                    approvalStatus = "Pending",
                    approvedBy = null,
                    approvalTimestamp = null
                )

                viewModel.submitAttendance(submission)
                // After marking attendance, update attendance stats in session and UI
                CoroutineScope(Dispatchers.IO).launch {
                    AttendanceStatsUpdater.updateAttendanceStatsInSession(requireContext())
                }
                Toast.makeText(requireContext(), "Attendance submitted successfully", Toast.LENGTH_SHORT).show()
                // Navigate to the Home tab
                findNavController().navigate(R.id.homeFragment)
            } catch (e: Exception) {
                Log.e("AttendanceManager", "Error submitting attendance", e)
                Toast.makeText(requireContext(), "Error submitting attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.studentProgressBar.visibility = View.GONE
                binding.markAttendanceButton.isEnabled = true
            }
        }
    }

    private fun setupLiveEventsAdapter(events: List<AttendanceEvent>) {
        val adapter = LiveEventsAdapter(
            events = events,
            onViewPendingClick = { event ->
                if (isAdmin) {
                    // TODO: Navigate to PendingAttendanceListFragment with event.getEventName()
                    // Example (using Navigation Component):
                    // findNavController().navigate(R.id.action_attendanceManagerFragment_to_pendingAttendanceListFragment, bundleOf("eventName" to event.getEventName()))

                    // Navigate to the new PendingAttendanceListFragment
                    val bundle = Bundle().apply {
                        putString("eventName", event.getEventName())
                    }
                    findNavController().navigate(R.id.action_attendanceManagerFragment_to_pendingAttendanceListFragment, bundle)

                } else {
                    showPendingAttendanceDialog(event)
                }
            },
            onCloseEventClick = { event ->
                closeEvent(event)
            }
        )
        binding.liveEventsRecyclerView?.adapter = adapter
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
} 