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
import com.phad.chatapp.utils.NotificationHelper
import android.content.Intent
import com.phad.chatapp.utils.InAppUpdateHelper

class NssMainActivity : AppCompatActivity() {
    private val TAG = "NssMainActivity"
    private lateinit var sessionManager: SessionManager
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nss_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Initialize session manager
        sessionManager = SessionManager(this)
        // Initialize notification helper
        notificationHelper = NotificationHelper(this)

        // Set up NavController for NSS
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up window insets for edge-to-edge
        setupWindowInsets()
        // Load user data - will also set up navigation
        loadUserData()
        // Start notification listener for the current user
        val currentUserId = sessionManager.fetchUserId()
        if (currentUserId.isNotEmpty()) {
            notificationHelper.startListeningForNotifications(currentUserId)
        }
        // Set up custom navigation buttons
        setupCustomNavigation()

        // Set up navigation destination change listener
        setupNavigationListener()
    }

    override fun onResume() {
        super.onResume()
        // Resume in-app update if needed
        InAppUpdateHelper.resumeIfUpdateInProgress(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == InAppUpdateHelper.UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                finishAffinity()
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