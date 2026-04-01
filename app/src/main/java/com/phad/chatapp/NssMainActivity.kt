package com.phad.chatapp

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.phad.chatapp.utils.SessionManager

import android.content.Intent
import android.content.pm.PackageManager
import com.phad.chatapp.activities.NotificationHistoryActivity
import com.phad.chatapp.activities.LoginActivity

class NssMainActivity : AppCompatActivity() {
    private val TAG = "NssMainActivity"
    private lateinit var sessionManager: SessionManager
    private lateinit var auth: FirebaseAuth

    private lateinit var navController: NavController

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    private var notificationDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nss_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Initialize session manager
        sessionManager = SessionManager(this)

        // Request notification permission if needed
        requestNotificationPermissionIfNeeded()

        // Set up NavController for NSS
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up window insets for edge-to-edge
        setupWindowInsets()
        // Load user data - will also set up navigation
        loadUserData()
        // Set up custom navigation buttons
        setupCustomNavigation()

        // Set up navigation destination change listener
        setupNavigationListener()
        
        // Setup Update Popup
        setupUpdatePopup()

        // If launched from a push notification tap, open notification history
        if (intent.getBooleanExtra("open_notifications", false)) {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_notifications", false)) {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Restart notification listener when returning to the app
        val currentUserId = sessionManager.fetchUserId()
        // notificationHelper removed, FCM handles all pushes
        
        checkAndEnforceNotificationPermission()
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            checkAndEnforceNotificationPermission()
        }
    }

    private fun checkAndEnforceNotificationPermission() {
        if (!com.phad.chatapp.utils.ChatMessagingService.areNotificationsEnabled(this)) {
            if (notificationDialog == null) {
                notificationDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Notifications Required")
                    .setMessage("Push notifications are mandatory for this app. Please enable them in your device settings to continue.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        openNotificationSettings()
                    }
                    .setNegativeButton("Exit") { _, _ ->
                        finishAffinity()
                    }
                    .create()
                notificationDialog?.show()
            } else if (notificationDialog?.isShowing == false) {
                notificationDialog?.show()
            }
        } else {
            notificationDialog?.dismiss()
            notificationDialog = null
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                }
                else -> {
                    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    addCategory(Intent.CATEGORY_DEFAULT)
                    data = android.net.Uri.parse("package:$packageName")
                }
            }
        }
        startActivity(intent)
    }

    private fun setupUpdatePopup() {
        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_update_overlay)
        val mainContent = findViewById<View>(R.id.nav_host_fragment)?.parent as? View 
            ?: findViewById<View>(R.id.main) // Fallback to root
        val bottomNav = findViewById<View>(R.id.bottom_nav_container)

        // State to hold current status
        val updateStatusState = androidx.compose.runtime.mutableStateOf(com.phad.chatapp.utils.InAppUpdateManager.UpdateStatus.NONE)
        
        composeView.setContent {
            val status = androidx.compose.runtime.remember { updateStatusState }
            if (status.value != com.phad.chatapp.utils.InAppUpdateManager.UpdateStatus.NONE) {
                // Ensure it's effectively on top
                composeView.visibility = View.VISIBLE
                composeView.bringToFront()
                
                // Apply Blur Effect to the underlying content (API 31+)
                if (Build.VERSION.SDK_INT >= 31) {
                    val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                        10f, 10f, android.graphics.Shader.TileMode.CLAMP
                    )
                    mainContent.setRenderEffect(blurEffect)
                    bottomNav?.setRenderEffect(blurEffect)
                }

                com.phad.chatapp.ui.components.UpdateOverlay(
                    updateStatus = status.value,
                    onDismissRequest = {
                        // For optional updates, allow distinct dismissal
                        status.value = com.phad.chatapp.utils.InAppUpdateManager.UpdateStatus.NONE
                        composeView.visibility = View.GONE
                        
                        // Remove Blur
                        if (Build.VERSION.SDK_INT >= 31) {
                            mainContent.setRenderEffect(null)
                            bottomNav?.setRenderEffect(null)
                        }
                    }
                )
            } else {
                composeView.visibility = View.GONE
                // Ensure blur is removed if state changes externally
                 if (Build.VERSION.SDK_INT >= 31) {
                    mainContent.setRenderEffect(null)
                    bottomNav?.setRenderEffect(null)
                }
            }
        }
        
        
        // Trigger check
        com.phad.chatapp.utils.InAppUpdateManager.checkForUpdates(this) { status ->
            runOnUiThread {
                updateStatusState.value = status
            }
        }
    }

    private fun setupWindowInsets() {
        val mainLayout = findViewById<View>(R.id.main)
        mainLayout?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(
                    left = systemBars.left,
                    top = systemBars.top,
                    right = systemBars.right,
                    bottom = systemBars.bottom
                )
                insets
            }
        }
    }

    private fun loadUserData() {
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Not logged in, redirect to login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        } else {
            // Get user data from session
            val userData = sessionManager.getUserDetails()
            // Set up the UI immediately to show home screen first
            setupNavigation()

            // Ensure FCM wing/role topic subscriptions are always up to date
            (application as? ChatApplication)?.subscribeToUserTopics(sessionManager)
        }
    }

    private fun setupNavigation() {
        // Initialize with NssHomeFragment
        if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
            navController.navigate(R.id.nssHomeFragment)
        }
        // Set up custom navigation buttons
        setupCustomNavigation()
    }

    private fun setupCustomNavigation() {
        val btnHome = findViewById<ImageButton>(R.id.btn_home)
        val btnCalendar = findViewById<ImageButton>(R.id.btn_calendar)
        val btnQRAttendance = findViewById<ImageButton>(R.id.btn_qr_attendance)
        val btnProfile = findViewById<ImageButton>(R.id.btn_profile)

        // Set initial selection
        btnHome.setColorFilter(ContextCompat.getColor(this, R.color.blue))

        btnHome.setOnClickListener {
            navController.navigate(R.id.nssHomeFragment)
            resetNavButtonColors()
            btnHome.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        btnCalendar.setOnClickListener {
            navController.navigate(R.id.nssCalendarFragment)
            resetNavButtonColors()
            btnCalendar.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        btnQRAttendance.setOnClickListener {
            navigateToQRAttendance()
            resetNavButtonColors()
            btnQRAttendance.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        btnProfile.setOnClickListener {
            navController.navigate(R.id.nssProfileFragment)
            resetNavButtonColors()
            btnProfile.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
    }

    private fun resetNavButtonColors() {
        findViewById<ImageButton>(R.id.btn_home).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_calendar).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_qr_attendance).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_profile).clearColorFilter()
    }

    /**
     * Set up navigation destination change listener to automatically update bottom navigation state
     */
    private fun setupNavigationListener() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("NssMainActivity", "Navigation destination changed to: ${destination.label} (id: ${destination.id})")

            // Reset all button colors first
            resetNavButtonColors()

            // Set the appropriate button color based on destination
            when (destination.id) {
                R.id.nssHomeFragment -> {
                    findViewById<ImageButton>(R.id.btn_home).setColorFilter(
                        ContextCompat.getColor(this, R.color.blue)
                    )
                    Log.d("NssMainActivity", "Home button selected")
                }
                R.id.nssCalendarFragment -> {
                    findViewById<ImageButton>(R.id.btn_calendar).setColorFilter(
                        ContextCompat.getColor(this, R.color.blue)
                    )
                    Log.d("NssMainActivity", "Calendar button selected")
                }
                R.id.nssQRAttendanceFragment, R.id.nssQRScanFragment -> {
                    findViewById<ImageButton>(R.id.btn_qr_attendance).setColorFilter(
                        ContextCompat.getColor(this, R.color.blue)
                    )
                    Log.d("NssMainActivity", "QR Attendance button selected")
                }
                R.id.qrAttendanceResultFragment -> {
                    // Don't highlight any bottom nav button for result screen
                    // This is a temporary screen that will navigate back to home
                    Log.d("NssMainActivity", "On QR result screen - no bottom nav button selected")
                }
                R.id.nssProfileFragment -> {
                    findViewById<ImageButton>(R.id.btn_profile).setColorFilter(
                        ContextCompat.getColor(this, R.color.blue)
                    )
                    Log.d("NssMainActivity", "Profile button selected")
                }
                else -> {
                    // For other destinations (like QRAttendanceResultFragment),
                    // don't highlight any bottom nav button
                    Log.d("NssMainActivity", "No bottom nav button selected for destination: ${destination.label}")
                }
            }
        }
    }

    private fun navigateToQRAttendance() {
        val userType = sessionManager.fetchUserType()
        val currentInterface = sessionManager.getLastInterfaceChoice() ?: "NSS"

        Log.d("NssMainActivity", "QR Attendance access check - UserType: '$userType', Interface: '$currentInterface'")

        // Allow admins regardless of interface
        if (userType.equals("Admin", ignoreCase = true)) {
            Log.d("NssMainActivity", "Admin user accessing QR attendance management")
            navController.navigate(R.id.nssQRAttendanceFragment)
        } else if (userType.equals("Student", ignoreCase = true)) {
            Log.d("NssMainActivity", "Student user accessing QR scan")
            navController.navigate(R.id.nssQRScanFragment)
        } else {
            Log.w("NssMainActivity", "Access denied - UserType: '$userType', Interface: '$currentInterface'")
            Toast.makeText(this, "Access denied. Only NSS users can access QR attendance.", Toast.LENGTH_SHORT).show()
        }
    }
}