package com.phad.chatapp.fragments

import android.Manifest
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
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.viewmodels.QRAttendanceViewModel
import com.phad.chatapp.viewmodels.QRAttendanceViewModelFactory
import com.phad.chatapp.viewmodels.ScanResult
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    
    // Permission handling
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }
    
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

        // Run debug test to verify QR flow
        viewModel.debugTestQRFlow()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nss_qr_scan, container, false)
        
        // Get camera preview view
        previewView = view.findViewById(R.id.camera_preview)

        // Set up zoom gesture detection on the preview view
        setupPreviewViewGestures()
        
        // Set up compose overlay
        val composeOverlay = view.findViewById<ComposeView>(R.id.compose_overlay)
        composeOverlay.setContent {
            val uiState by viewModel.studentUiState.collectAsState()

            // Show processing overlay with dimmed home background when camera has exited
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
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check camera permission and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

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
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
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
                
                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    // Unbind use cases before rebinding
                    cameraProvider?.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                    )

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

                    Log.d(TAG, "Camera started successfully")
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

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
    
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "QR Scanner",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan QR Code for Attendance",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Student: ${uiState.studentName}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

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
