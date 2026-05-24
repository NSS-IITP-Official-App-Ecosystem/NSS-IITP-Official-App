package com.phad.chatapp.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.phad.chatapp.R
import com.phad.chatapp.ui.components.DimmedHomeBackground
import com.phad.chatapp.ui.components.GradientHeader
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.LocationPermissionHelper
import com.phad.chatapp.utils.PlayIntegrityManager
import com.phad.chatapp.viewmodels.QRAttendanceViewModel
import com.phad.chatapp.viewmodels.QRAttendanceViewModelFactory
import com.phad.chatapp.viewmodels.ScanResult
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.ExifInterface
import android.provider.MediaStore
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import com.phad.chatapp.models.AttendanceEvent
import com.phad.chatapp.utils.PhotoAttendanceManager
import com.phad.chatapp.services.LocationService
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AttendanceMode {
    SELECT,
    SCAN_QR,
    CAPTURE_PHOTO
}

/**
 * Fragment for Student QR Scanning - Give Attendance functionality
 * Only accessible to Student users in NSS interface
 */
class NssQRScanFragment : Fragment() {
    private val TAG = "NssQRScanFragment"
    
    private lateinit var viewModel: QRAttendanceViewModel
    private lateinit var sessionManager: SessionManager
    
    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    
    // Barcode scanner
    private val barcodeScanner = BarcodeScanning.getClient()
    
    private var imageCapture: ImageCapture? = null
    private var attendanceMode by mutableStateOf(AttendanceMode.SELECT)
    private var selectedEventForPhoto by mutableStateOf<AttendanceEvent?>(null)
    private var isUploadingPhoto by mutableStateOf(false)
    private var uploadErrorMsg by mutableStateOf<String?>(null)
    private var mockLocationDetected by mutableStateOf(false)
    private var isFrontCamera by mutableStateOf(false)
    private var flashMode by mutableStateOf(ImageCapture.FLASH_MODE_OFF)
    
    
    // Permission handling for camera and location
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false ||
                             permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (cameraGranted && locationGranted) {
            // Both permissions granted, check if location is enabled
            checkLocationEnabledAndStart()
        } else if (!cameraGranted) {
            Toast.makeText(requireContext(), "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        } else if (!locationGranted) {
            Toast.makeText(requireContext(), "Location permission is required for attendance verification", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }
    
    // State for showing location dialog
    private var showLocationDialog by mutableStateOf(false)
    
    // State for Play Integrity verification
    private var integrityCheckState by mutableStateOf<PlayIntegrityManager.IntegrityResult?>(null)
    private var showIntegrityBlockedDialog by mutableStateOf(false)
    private var integrityErrorMessage by mutableStateOf("")

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        
        // Check if user is student (case-insensitive)
        val userType = sessionManager.fetchUserType()
        if (!userType.equals("Student", ignoreCase = true)) {
            Log.w(TAG, "Non-student user trying to access QR scanning: $userType")
            Toast.makeText(requireContext(), "Access denied. Student privileges required.", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
            return
        }
        
        // Initialize ViewModel
        val factory = QRAttendanceViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[QRAttendanceViewModel::class.java]
        
        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        Log.d(TAG, "NssQRScanFragment created for student: ${sessionManager.fetchUserName()}")
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nss_qr_scan, container, false)
        
        // Get camera preview view
        previewView = view.findViewById(R.id.camera_preview)
        previewView.visibility = View.GONE // Hide camera by default until a mode is chosen

        // Set up zoom gesture detection on the preview view
        setupPreviewViewGestures()
        
        // Set up compose overlay
        val composeOverlay = view.findViewById<ComposeView>(R.id.compose_overlay)
        composeOverlay.setContent {
            val uiState by viewModel.studentUiState.collectAsState()
            val adminState by viewModel.adminUiState.collectAsState()

            // Show integrity check loading state
            val currentIntegrityState = integrityCheckState
            if (currentIntegrityState == PlayIntegrityManager.IntegrityResult.Loading) {
                IntegrityCheckLoadingDialog()
            }
            
            // Show integrity blocked dialog if verification failed
            if (showIntegrityBlockedDialog) {
                IntegrityBlockedDialog(
                    message = integrityErrorMessage,
                    canRetry = (currentIntegrityState as? PlayIntegrityManager.IntegrityResult.Failure)?.canRetry ?: false,
                    onRetry = {
                        showIntegrityBlockedDialog = false
                        performIntegrityCheck()
                    },
                    onDismiss = {
                        showIntegrityBlockedDialog = false
                        parentFragmentManager.popBackStack()
                    }
                )
            }

            // Show location dialog if needed
            if (showLocationDialog) {
                LocationEnableDialog(
                    onEnableClick = {
                        LocationPermissionHelper.openLocationSettings(requireContext())
                        showLocationDialog = false
                    },
                    onDismiss = {
                        showLocationDialog = false
                        parentFragmentManager.popBackStack()
                    }
                )
            }

            // Route based on attendanceMode
            when (attendanceMode) {
                AttendanceMode.SELECT -> {
                    ModeSelectionScreen(
                        studentName = sessionManager.fetchUserName() ?: "Student",
                        onModeSelected = { mode ->
                            if (mode == AttendanceMode.CAPTURE_PHOTO) {
                                viewModel.loadAvailableEvents()
                            }
                            onModeChanged(mode)
                        }
                    )
                }
                AttendanceMode.SCAN_QR -> {
                    if (uiState.cameraExited && uiState.isProcessing) {
                        ProcessingOverlayWithHomeBackground()
                    } else {
                        QRScanOverlay(
                            uiState = uiState,
                            onRetryClick = {
                                viewModel.clearError()
                                startCamera()
                            }
                        )
                    }
                }
                AttendanceMode.CAPTURE_PHOTO -> {
                    PhotoCaptureOverlay(
                        availableEvents = adminState.availableEvents,
                        selectedEvent = selectedEventForPhoto,
                        onEventSelected = { selectedEventForPhoto = it },
                        onCaptureClick = {
                            selectedEventForPhoto?.let { event ->
                                captureAndUploadPhoto(event)
                            }
                        },
                        onBackClick = {
                            onModeChanged(AttendanceMode.SELECT)
                        },
                        isLoadingEvents = adminState.isLoading,
                        isUploading = isUploadingPhoto,
                        uploadError = uploadErrorMsg,
                        onClearError = { uploadErrorMsg = null },
                        mockDetected = mockLocationDetected,
                        onDismissMockDialog = { mockLocationDetected = false },
                        isFrontCamera = isFrontCamera,
                        onSwitchCamera = { toggleCamera() },
                        flashMode = flashMode,
                        onFlashModeChanged = { cycleFlashMode() },
                        currentZoomLevel = uiState.currentZoomLevel,
                        minZoomLevel = uiState.minZoomLevel,
                        maxZoomLevel = uiState.maxZoomLevel,
                        onZoomChanged = { viewModel.updateZoomLevel(it) }
                    )
                }
            }
        }
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Play Integrity verification - server-side check before allowing QR scanning
        // Modified Logic:
        // 1. PlayIntegrityManager.verifyIntegrity() now internally checks cache (< 24h).
        // 2. If valid, it returns Success IMMEDIATELY (non-blocking).
        // 3. If expired, it performs the network check.
        // This solves the "Crowded Space" issue by using yesterday's cached success.
        Log.d(TAG, "Starting Play Integrity verification (Cache-aware)...")
        performIntegrityCheck()

        // Observe UI state for navigation and camera control
        lifecycleScope.launch {
            viewModel.studentUiState.collect { state ->
                // Handle immediate camera exit when processing starts
                if (state.isProcessing && !state.cameraExited) {
                    Log.d(TAG, "QR processing started - exiting camera immediately")
                    stopCamera()
                    showProcessingOverlay()
                    viewModel.markCameraExited()
                }

                // Handle navigation to result screens
                when (val result = state.scanResult) {
                    is ScanResult.Success -> {
                        if (state.cameraExited && !state.navigatedToResult) {
                            Log.d(TAG, "Navigating to success screen: ${result.message}")
                            navigateToSuccessScreen(result.message)
                            viewModel.markNavigatedToResult()
                        }
                    }
                    is ScanResult.Error -> {
                        if (state.cameraExited && !state.navigatedToResult) {
                            Log.d(TAG, "Navigating to error screen: ${result.message}")
                            navigateToErrorScreen(result.message)
                            viewModel.markNavigatedToResult()
                        }
                    }
                    null -> { /* No result yet */ }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start periodic location refresh every 10s while on scan screen
        viewModel.startStudentLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop refreshing location when leaving scan screen
        viewModel.stopStudentLocationUpdates()
    }
    
    /**
     * Perform Play Integrity verification before allowing QR scanning.
     * This is an async operation that updates UI state based on result.
     */
    private fun performIntegrityCheck() {
        integrityCheckState = PlayIntegrityManager.IntegrityResult.Loading
        
        lifecycleScope.launch {
            val userId = sessionManager.fetchRollNumber() ?: "unknown"
            Log.d(TAG, "Verifying integrity for user: $userId")
            
            val result = PlayIntegrityManager.verifyIntegrity(requireContext(), userId)
            integrityCheckState = result
            
            when (result) {
                is PlayIntegrityManager.IntegrityResult.Success -> {
                    Log.d(TAG, "Integrity verification passed - showing selection menu")
                    onModeChanged(AttendanceMode.SELECT)
                }
                is PlayIntegrityManager.IntegrityResult.Failure -> {
                    Log.w(TAG, "Integrity verification failed: ${result.message}")
                    integrityErrorMessage = result.message
                    showIntegrityBlockedDialog = true
                }
                is PlayIntegrityManager.IntegrityResult.Loading -> {
                    // Should not reach here
                }
            }
        }
    }

    private fun onModeChanged(mode: AttendanceMode) {
        attendanceMode = mode
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav_container)
        if (mode == AttendanceMode.SELECT) {
            stopCamera()
            previewView.visibility = View.GONE
            bottomNav?.visibility = View.VISIBLE
        } else {
            previewView.visibility = View.VISIBLE
            requestPermissionsIfNeeded()
            bottomNav?.visibility = View.GONE
        }
    }
    
    private fun allPermissionsGranted(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationGranted = LocationPermissionHelper.hasLocationPermission(requireContext())
        
        return cameraGranted && locationGranted
    }
    
    private fun requestPermissionsIfNeeded() {
        if (!allPermissionsGranted()) {
            // Request camera + location permissions
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Permissions granted, check if location is enabled
            checkLocationEnabledAndStart()
        }
    }
    
    private fun checkLocationEnabledAndStart() {
        if (!LocationPermissionHelper.isLocationEnabled(requireContext())) {
            // Show dialog asking user to enable location
            showLocationDialog = true
        } else {
            // All good, start camera
            startCamera()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                // Camera provider is now guaranteed to be available
                cameraProvider = cameraProviderFuture.get()
                
                // Set up camera preview
                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                // Select front or back camera dynamically
                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                
                try {
                    // Unbind use cases before rebinding
                    cameraProvider?.unbindAll()

                    if (attendanceMode == AttendanceMode.SCAN_QR) {
                        // Set up image analyzer for QR code scanning
                        imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrText ->
                                    // Process the scanned QR code
                                    lifecycleScope.launch {
                                        viewModel.processScannedQR(qrText)
                                    }
                                })
                            }

                        // Bind use cases to camera
                        camera = cameraProvider?.bindToLifecycle(
                            this, cameraSelector, preview, imageAnalyzer
                        )
                    } else if (attendanceMode == AttendanceMode.CAPTURE_PHOTO) {
                        // Set up image capture for geo-tagged photo
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(flashMode)
                            .build()

                        // Bind use cases to camera
                        camera = cameraProvider?.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture
                        )
                    }

                    // Initialize zoom capabilities
                    camera?.let { cam ->
                        val cameraInfo = cam.cameraInfo
                        val zoomState = cameraInfo.zoomState.value
                        if (zoomState != null) {
                            viewModel.setZoomLimits(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                            Log.d(TAG, "Zoom capabilities: ${zoomState.minZoomRatio}x - ${zoomState.maxZoomRatio}x")
                        }

                        // Set up zoom gesture handling
                        setupZoomGestures()
                    }

                    Log.d(TAG, "Camera started successfully in mode: $attendanceMode")
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    Toast.makeText(requireContext(), "Failed to start camera", Toast.LENGTH_SHORT).show()
                }
                
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                Toast.makeText(requireContext(), "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        try {
            Log.d(TAG, "Stopping camera")
            cameraProvider?.unbindAll()
            previewView.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    private fun toggleCamera() {
        isFrontCamera = !isFrontCamera
        Log.d(TAG, "Toggling camera. isFrontCamera: $isFrontCamera")
        startCamera()
    }

    private fun cycleFlashMode() {
        val nextFlash = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_OFF
        }
        flashMode = nextFlash
        Log.d(TAG, "Cycling flash mode. New flashMode: $flashMode")
        try {
            imageCapture?.flashMode = flashMode
        } catch (e: Exception) {
            Log.e(TAG, "Error setting flash mode on imageCapture", e)
        }
    }

    private fun showProcessingOverlay() {
        // The processing overlay is now handled in the compose content
        // This method is kept for potential future use
        Log.d(TAG, "Showing processing overlay with dimmed home background")
    }

    private fun navigateToSuccessScreen(message: String) {
        val bundle = Bundle().apply {
            putString("result_message", message)
            putString("result_type", "success")
        }
        findNavController().navigate(R.id.qrAttendanceResultFragment, bundle)
    }

    private fun navigateToErrorScreen(message: String) {
        val bundle = Bundle().apply {
            putString("result_message", message)
            putString("result_type", "error")
        }
        findNavController().navigate(R.id.qrAttendanceResultFragment, bundle)
    }

    // ========== Zoom Control Methods ==========

    private fun setupZoomGestures() {
        // Observe zoom level changes from ViewModel and apply to camera
        lifecycleScope.launch {
            viewModel.studentUiState.collect { state ->
                camera?.let { cam ->
                    try {
                        val cameraControl = cam.cameraControl
                        val zoomFuture = cameraControl.setZoomRatio(state.currentZoomLevel)
                        Log.d(TAG, "Zoom level applied: ${String.format("%.1f", state.currentZoomLevel)}x")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying zoom level: ${state.currentZoomLevel}", e)
                    }
                }
            }
        }
    }

    /**
     * Handle pinch-to-zoom gesture
     */
    fun handleZoomGesture(scaleFactor: Float) {
        Log.d(TAG, "handleZoomGesture called with scaleFactor: $scaleFactor")

        camera?.let { cam ->
            val currentState = viewModel.studentUiState.value
            val currentZoom = currentState.currentZoomLevel
            val newZoom = (currentZoom * scaleFactor).coerceIn(
                currentState.minZoomLevel,
                currentState.maxZoomLevel
            )

            Log.d(TAG, "Zoom calculation: current=${String.format("%.1f", currentZoom)}x, " +
                    "scaleFactor=$scaleFactor, new=${String.format("%.1f", newZoom)}x, " +
                    "limits=${String.format("%.1f", currentState.minZoomLevel)}x-${String.format("%.1f", currentState.maxZoomLevel)}x")

            if (newZoom != currentZoom) {
                Log.d(TAG, "Updating zoom level from ${String.format("%.1f", currentZoom)}x to ${String.format("%.1f", newZoom)}x")
                viewModel.updateZoomLevel(newZoom)
            } else {
                Log.d(TAG, "Zoom level unchanged: ${String.format("%.1f", currentZoom)}x")
            }
        } ?: run {
            Log.w(TAG, "handleZoomGesture called but camera is null")
        }
    }

    /**
     * Set zooming visual feedback state
     */
    fun setZoomingFeedback(isZooming: Boolean) {
        Log.d(TAG, "setZoomingFeedback called with isZooming: $isZooming")
        viewModel.setZoomingState(isZooming)
    }

    /**
     * Set up gesture detection on the PreviewView for zoom functionality
     */
    private fun setupPreviewViewGestures() {
        val scaleGestureDetector = android.view.ScaleGestureDetector(
            requireContext(),
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                    Log.d(TAG, "onScaleBegin: scaleFactor=${detector.scaleFactor}")
                    setZoomingFeedback(true)
                    return true
                }

                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    Log.d(TAG, "onScale: scaleFactor=$scaleFactor")
                    handleZoomGesture(scaleFactor)
                    return true
                }

                override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                    Log.d(TAG, "onScaleEnd")
                    setZoomingFeedback(false)
                }
            }
        )

        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        Log.d(TAG, "Preview view gesture detection set up")
    }

    private fun captureAndUploadPhoto(event: AttendanceEvent) {
        val imageCaptureObj = imageCapture ?: run {
            Toast.makeText(requireContext(), "Camera not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        isUploadingPhoto = true
        uploadErrorMsg = null

        val locationService = LocationService(requireContext())
        locationService.getFreshHighAccuracyLocation { location ->
            if (location == null) {
                isUploadingPhoto = false
                Toast.makeText(requireContext(), "Failed to get GPS location. Ensure location is enabled.", Toast.LENGTH_LONG).show()
                return@getFreshHighAccuracyLocation
            }

            // Check for mock location provider (Fake GPS prevention)
            val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock
            } else {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }

            if (isMock) {
                isUploadingPhoto = false
                mockLocationDetected = true
                return@getFreshHighAccuracyLocation
            }

            val tempFile = File(requireContext().cacheDir, "temp_attendance_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

            imageCaptureObj.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lifecycleScope.launch {
                            try {
                                var bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
                                bitmap = rotateBitmapIfRequired(bitmap, tempFile.absolutePath)

                                val address = getAddressFromLocation(requireContext(), location.latitude, location.longitude)
                                val watermarkedBitmap = applyWatermark(bitmap, location.latitude, location.longitude, address)

                                savePhotoToGallery(watermarkedBitmap)

                                val bos = ByteArrayOutputStream()
                                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                                val imageBytes = bos.toByteArray()

                                bitmap.recycle()
                                watermarkedBitmap.recycle()
                                if (tempFile.exists()) tempFile.delete()

                                val rollNo = sessionManager.fetchRollNumber() ?: "unknown"
                                Log.d(TAG, "Uploading photo attendance. User: $rollNo, Event: ${event.id}")
                                val result = PhotoAttendanceManager.submitPhotoAttendance(
                                    userId = rollNo,
                                    eventId = event.id,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    imageBytes = imageBytes
                                )

                                isUploadingPhoto = false
                                result.fold(
                                    onSuccess = {
                                        Log.d(TAG, "Photo attendance submitted successfully")
                                        navigateToSuccessScreen("Geo-tagged photo submitted successfully!\nVerification pending admin approval.")
                                    },
                                    onFailure = { error ->
                                        Log.e(TAG, "Photo attendance submission failed", error)
                                        uploadErrorMsg = error.message ?: "Failed to upload photo"
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing captured photo", e)
                                isUploadingPhoto = false
                                uploadErrorMsg = "Failed to process photo: ${e.message}"
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        isUploadingPhoto = false
                        uploadErrorMsg = "Failed to capture photo: ${exception.message}"
                    }
                }
            )
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = android.graphics.Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            bitmap
        }
    }

    private suspend fun getAddressFromLocation(context: Context, lat: Double, lon: Double): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val geocoder = android.location.Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val fullAddress = address.getAddressLine(0)
                if (!fullAddress.isNullOrEmpty()) {
                    val parts = fullAddress.split(",")
                    parts.take(3).joinToString(",").trim()
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed", e)
            ""
        }
    }

    private fun applyWatermark(src: Bitmap, lat: Double, lon: Double, address: String): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)

        val overlayHeight = (height * 0.16f).coerceIn(160f, 380f)
        
        val rectPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#99000000") // 60% transparent black
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, height - overlayHeight, width.toFloat(), height.toFloat(), rectPaint)

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (overlayHeight * 0.18f).coerceIn(24f, 54f)
            isAntiAlias = true
        }

        val padding = 30f
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
        val dateStr = "Time: " + sdf.format(Date())
        val locStr = String.format("GPS: %.6f, %.6f", lat, lon)
        val placeStr = if (address.isNotEmpty()) "Place: $address" else "Place: Location Acquired"

        canvas.drawText(placeStr, padding, height - (overlayHeight * 0.70f), textPaint)
        canvas.drawText(locStr, padding, height - (overlayHeight * 0.42f), textPaint)
        canvas.drawText(dateStr, padding, height - (overlayHeight * 0.14f), textPaint)

        return result
    }

    private fun savePhotoToGallery(bitmap: Bitmap) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "NSS_Attendance_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/NSS_Attendance")
                }
            }
            val uri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                contentValues
            )
            uri?.let {
                requireContext().contentResolver.openOutputStream(it).use { out ->
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        Log.i(TAG, "Successfully saved a copy of the geotagged photo to gallery: $uri")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo to gallery", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav_container)
        bottomNav?.visibility = View.VISIBLE
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
    
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

/**
 * Image analyzer for QR code detection using ML Kit
 */
private class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val barcodeScanner = BarcodeScanning.getClient()
    private var lastAnalyzedTimestamp = 0L
    private var lastDetectedQRCode: String? = null
    private var lastDetectionTime = 0L
    
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Only analyze every 100ms to reduce latency while avoiding excessive processing
        if (currentTimestamp - lastAnalyzedTimestamp >= 100L) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            Log.d("QRCodeAnalyzer", "Found ${barcodes.size} barcodes")
                        }
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT -> {
                                    barcode.displayValue?.let { qrText ->
                                        Log.d("QRCodeAnalyzer", "QR Code detected: ${qrText.take(50)}...")

                                        // Debounce: Only process if it's a different QR code or enough time has passed
                                        val currentTime = System.currentTimeMillis()
                                        val isDifferentQR = qrText != lastDetectedQRCode
                                        val enoughTimePassed = currentTime - lastDetectionTime > 1000L // 1 second debounce

                                        if (isDifferentQR || enoughTimePassed) {
                                            Log.d("QRCodeAnalyzer", "Processing QR code - Different: $isDifferentQR, Time passed: ${currentTime - lastDetectionTime}ms")
                                            lastDetectedQRCode = qrText
                                            lastDetectionTime = currentTime
                                            onQRCodeDetected(qrText)
                                        } else {
                                            Log.d("QRCodeAnalyzer", "Skipping duplicate QR code detection - Time since last: ${currentTime - lastDetectionTime}ms")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("QRCodeAnalyzer", "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
            lastAnalyzedTimestamp = currentTimestamp
        } else {
            imageProxy.close()
        }
    }
}

@Composable
fun QRScanOverlay(
    uiState: com.phad.chatapp.viewmodels.StudentQRUiState,
    onRetryClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top header - center-aligned content in blue header box
        GradientHeader(
            title = "Scan QR Code",
            subtitle = "Student: ${uiState.studentName}",
            icon = Icons.Default.QrCodeScanner,
            modifier = Modifier.align(Alignment.TopCenter),
            isTitleCentered = true
        )

        // Scanning frame overlay
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
        ) {
            // Scanning frame corners
            ScanningFrame()
        }

        // Zoom level indicator
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${String.format("%.1f", uiState.currentZoomLevel)}x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Visual feedback during zoom
                if (uiState.isZooming) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                Color(0xFF4CAF50),
                                CircleShape
                            )
                    )
                }
            }
        }

        // Bottom status area removed as requested
    }

    // Show processing overlay when QR code is being processed
    if (uiState.isProcessing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2196F3),
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp
                )
                Text(
                    text = "Processing QR Code...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Please wait while we validate your attendance",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Success and error handling is now done via dedicated result screens
    // No dialog needed here anymore
}

@Composable
fun ScanningFrame() {
    // Simple scanning frame with corner indicators
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Corner indicators would go here
        // For now, just a simple border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
        )
    }
}

@Composable
fun ProcessingOverlayWithHomeBackground() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dimmed home background
        DimmedHomeBackground()

        // Processing indicator overlay
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF2196F3)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Processing QR Code...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Please wait while we validate your attendance",
                        fontSize = 14.sp,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// DimmedHomeBackground moved to shared component

@Composable
fun LocationEnableDialog(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Location Required",
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Location Required",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Please enable location to mark attendance.",
                    fontSize = 16.sp,
                    color = Color(0xFF212121)
                )
                Text(
                    text = "We use your location to verify you're at the event.",
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Enable Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF757575))
            }
        }
    )
}

/**
 * Loading dialog shown while Play Integrity verification is in progress
 */
@Composable
fun IntegrityCheckLoadingDialog() {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable during verification */ },
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFF2196F3)
            )
        },
        title = {
            Text(
                text = "Verifying App",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = "Please wait while we verify your app...",
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = { /* No buttons during loading */ },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Dialog shown when Play Integrity verification fails.
 * Shows a polite message asking user to install from Play Store.
 */
@Composable
fun IntegrityBlockedDialog(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Verification Failed",
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(56.dp)
            )
        },
        title = {
            Text(
                text = "Feature Unavailable",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF4B4B4B)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = Color(0xFF4B4B4B),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text("OK", fontSize = 16.sp)
            }
        },
        dismissButton = {
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = Color(0xFF757575))
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ModeSelectionScreen(
    studentName: String,
    onModeSelected: (AttendanceMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)) // Soft slate background
    ) {
        GradientHeader(
            title = "Attendance Verification",
            subtitle = "Student: $studentName",
            icon = Icons.Default.QrCodeScanner,
            onBackClick = null,
            isTitleCentered = true
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(top = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Verification Method",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Select a method below to verify your location and mark attendance for today's scheduled event.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scan QR Button Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .clickable { onModeSelected(AttendanceMode.SCAN_QR) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD))
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan QR",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan the QR code displayed on the coordinator's screen.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Go",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Capture Photo Button Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .clickable { onModeSelected(AttendanceMode.CAPTURE_PHOTO) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFDCFCE7), Color(0xFFBBF7D0))
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Photo",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Geo-Tagged Photo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Take a photo at the event location. GPS coordinates and place name will be embedded.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Go",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoCaptureOverlay(
    availableEvents: List<AttendanceEvent>,
    selectedEvent: AttendanceEvent?,
    onEventSelected: (AttendanceEvent?) -> Unit,
    onCaptureClick: () -> Unit,
    onBackClick: () -> Unit,
    isLoadingEvents: Boolean,
    isUploading: Boolean,
    uploadError: String?,
    onClearError: () -> Unit,
    mockDetected: Boolean,
    onDismissMockDialog: () -> Unit,
    isFrontCamera: Boolean,
    onSwitchCamera: () -> Unit,
    flashMode: Int,
    onFlashModeChanged: (Int) -> Unit,
    currentZoomLevel: Float,
    minZoomLevel: Float,
    maxZoomLevel: Float,
    onZoomChanged: (Float) -> Unit
) {
    val currentEvent = selectedEvent
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Grid overlay
        if (currentEvent != null && showGrid) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val color = Color.White.copy(alpha = 0.4f)
                val strokeWidth = 1.dp.toPx()

                // Draw vertical grid lines
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(width / 3f, 0f),
                    end = androidx.compose.ui.geometry.Offset(width / 3f, height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(width * 2f / 3f, 0f),
                    end = androidx.compose.ui.geometry.Offset(width * 2f / 3f, height),
                    strokeWidth = strokeWidth
                )

                // Draw horizontal grid lines
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, height / 3f),
                    end = androidx.compose.ui.geometry.Offset(width, height / 3f),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, height * 2f / 3f),
                    end = androidx.compose.ui.geometry.Offset(width, height * 2f / 3f),
                    strokeWidth = strokeWidth
                )
            }
        }

        val headerActions: @Composable (RowScope.() -> Unit)? = if (currentEvent != null) {
            {
                // Grid Toggle Button
                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        imageVector = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Toggle Grid",
                        tint = Color.White
                    )
                }

                // Flash Toggle Button
                IconButton(onClick = {
                    val nextFlash = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    onFlashModeChanged(nextFlash)
                }) {
                    val flashIcon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Toggle Flash",
                        tint = Color.White
                    )
                }

                // Edit/Change Event Button
                IconButton(onClick = { onEventSelected(null) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change Event",
                        tint = Color.White
                    )
                }
            }
        } else {
            null
        }

        // Gradient Header
        GradientHeader(
            title = "Capture Geotagged Photo",
            subtitle = currentEvent?.let { "Event: ${it.getEventName()}" } ?: "Select Event Below",
            icon = Icons.Default.CameraAlt,
            onBackClick = onBackClick,
            isTitleCentered = true,
            actions = headerActions
        )

        if (currentEvent == null) {
            // Event Selector Dropdown Card at the Center of the screen
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "NSS Event",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Event...",
                                fontSize = 15.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Dropdown",
                                tint = Color.Gray
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        if (isLoadingEvents) {
                            DropdownMenuItem(
                                text = { Text("Loading events...") },
                                onClick = {}
                            )
                        } else if (availableEvents.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No live events available") },
                                onClick = {}
                            )
                        } else {
                            availableEvents.forEach { event ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(event.getEventName(), fontWeight = FontWeight.Bold)
                                            Text("${event.eventDate} (${event.eventTime})", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        onEventSelected(event)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Shutter & Controls Container
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(bottom = 40.dp, top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Zoom Quick Selectors
                if (currentEvent != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val zoomOptions = listOf(1.0f, 2.0f)
                        zoomOptions.forEach { zoomOption ->
                            val targetZoom = zoomOption.coerceIn(minZoomLevel, maxZoomLevel)
                            val isSelected = Math.abs(currentZoomLevel - targetZoom) < 0.1f
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFF2196F3) else Color.Black.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { onZoomChanged(targetZoom) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${zoomOption.toInt()}x",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Make sure you are at the event site",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left spacer for balancing the row
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Empty spacer
                    }

                    // Shutter button (Center)
                    Box(
                        modifier = Modifier.weight(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color.Transparent, CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .clickable(enabled = currentEvent != null && !isUploading) { onCaptureClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(if (currentEvent != null) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }

                    // Switch camera button (Right)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentEvent != null) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    .clickable(!isUploading) { onSwitchCamera() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Switch Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Uploading Overlay
        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Text(
                            text = "Uploading Photo Attendance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Please wait, embedding GPS coordinates and syncing with server...",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Mock GPS Rejection Dialog
        if (mockDetected) {
            AlertDialog(
                onDismissRequest = onDismissMockDialog,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Fake GPS Detected",
                        tint = Color.Red,
                        modifier = Modifier.size(56.dp)
                    )
                },
                title = {
                    Text("GPS Spoofing Detected", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Text("Fake GPS/Mock location provider application has been detected. Please disable all mock location applications in developer options and try again.", fontSize = 14.sp)
                },
                confirmButton = {
                    Button(
                        onClick = onDismissMockDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        // Upload Error Dialog
        if (uploadError != null) {
            AlertDialog(
                onDismissRequest = onClearError,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Upload Error",
                        tint = Color.Red,
                        modifier = Modifier.size(56.dp)
                    )
                },
                title = {
                    Text("Submission Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Text(uploadError, fontSize = 14.sp)
                },
                confirmButton = {
                    Button(
                        onClick = onClearError,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Try Again")
                    }
                }
            )
        }
    }
}

