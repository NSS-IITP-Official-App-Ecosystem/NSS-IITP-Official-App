package com.phad.chatapp

import android.content.Intent
import com.phad.chatapp.activities.LoginActivity
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.utils.FirestoreSetup
import com.phad.chatapp.utils.NetworkUtils
import com.phad.chatapp.utils.SessionManager

import com.phad.chatapp.utils.MultiDatabaseHelper
import com.phad.chatapp.fragments.HomeFragment
import com.phad.chatapp.features.calendar.ui.CalendarFragment
import com.phad.chatapp.fragments.ProfileFragment

import com.phad.chatapp.features.scheduling.SchedulingFragment
import android.widget.ImageButton
import com.phad.chatapp.activities.NotificationHistoryActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var sessionManager: SessionManager
    private lateinit var auth: FirebaseAuth

    private lateinit var navController: NavController

    // User data
    private var userType: String? = null
    private var userRoll: String? = null
    private var userYear: Int = 0
    
    // Request code for notification permission
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    private var notificationDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate start")
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "after super.onCreate")

        // Removed enable edge to edge display - handling insets manually
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Request notification permission if needed for Android 13+
        requestNotificationPermissionIfNeeded()
        
        // Initialize session manager
        sessionManager = SessionManager(this)
        

        
        // Set up window flags for proper status bar handling
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set status bar color to transparent to allow content to draw behind it
        window.statusBarColor = getColor(android.R.color.transparent)
        
        // Make status bar icons light for better visibility on dark background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        
        setContentView(R.layout.activity_main)
        Log.d("MainActivity", "after setContentView")

        // Set up NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Removed action bar setup - toolbar is handled by fragments now
        
        // Set up window insets for edge-to-edge
        setupWindowInsets()
        Log.d("MainActivity", "after setupWindowInsets")

        // Load user data - will also set up navigation
        loadUserData()
        Log.d("MainActivity", "after loadUserData")



        // Set up custom navigation buttons
        setupCustomNavigation()
        Log.d("MainActivity", "after setupCustomNavigation")

        // If launched from a push notification tap, handle routing
        handleIntentRouting(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentRouting(intent)
    }

    private fun handleIntentRouting(intent: Intent) {
        val groupId = intent.getStringExtra("groupId")
        val senderId = intent.getStringExtra("senderId")
        val shouldOpenNotifications = intent.getBooleanExtra("open_notifications", false) || 
            intent.getStringExtra("type") == "LEAVE_NOTIFICATION" || 
            intent.extras?.containsKey("relatedId") == true

        if (!groupId.isNullOrEmpty()) {
            val groupName = intent.getStringExtra("groupName") ?: "Group Chat"
            val chatIntent = Intent(this, com.phad.chatapp.activities.GroupChatActivity::class.java).apply {
                putExtra("GROUP_ID", groupId)
                putExtra("GROUP_NAME", groupName)
            }
            startActivity(chatIntent)
            intent.removeExtra("groupId")
        } else if (!senderId.isNullOrEmpty()) {
            val senderName = intent.getStringExtra("senderName") ?: "User"
            val currentUserRollNumber = sessionManager.fetchUserId()
            val chatIntent = Intent(this, com.phad.chatapp.activities.ChatActivity::class.java).apply {
                putExtra("otherUserRollNumber", senderId)
                putExtra("otherUserName", senderName)
                putExtra("currentUserRollNumber", currentUserRollNumber)
                putExtra("currentUserName", sessionManager.fetchUserName())
            }
            startActivity(chatIntent)
            intent.removeExtra("senderId")
        } else if (shouldOpenNotifications) {
            val notifIntent = Intent(this, NotificationHistoryActivity::class.java).apply {
                intent.extras?.let { putExtras(it) }
            }
            startActivity(notifIntent)
        }
    }
    
    override fun onResume() {
        Log.d("MainActivity", "onResume start")
        super.onResume()
        Log.d("MainActivity", "after super.onResume")

        // Removed notification helper since we use Cloud Functions
        
        checkAndEnforceNotificationPermission()
        
        Log.d("MainActivity", "onResume end")
    }
    

    /**
     * Request notification permission for Android 13+
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted")
                checkAndEnforceNotificationPermission()
            } else {
                Log.w(TAG, "Notification permission denied - notifications won't work")
                checkAndEnforceNotificationPermission()
            }
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
            // addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Not strictly needed when calling from Activity
        }
        startActivity(intent)
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // Get user type from session
        val userType = sessionManager.fetchUserType()
        
        // Show/hide admin menu items based on user type (consolidated)
        val isAdmin = userType.equals("Admin", ignoreCase = true)

        // Only Admin can create/remove groups
        menu.findItem(R.id.action_create_group)?.isVisible = isAdmin
        menu.findItem(R.id.action_remove_group)?.isVisible = isAdmin
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                Toast.makeText(this, "Search not implemented yet", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_create_group -> {
                showCreateGroupScreen()
                true
            }
            R.id.action_remove_group -> {
                showRemoveGroupScreen()
                true
            }
            R.id.action_logout -> {
                sessionManager.logoutUser()
                redirectToLogin()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun diagnoseAndFixFirebaseConfig() {
        Log.d(TAG, "Diagnosing Firebase configuration...")
        val (isFixed, message) = FirestoreSetup.diagnoseAndFixFirebaseConfig(this)
        
        if (!isFixed) {
            Log.e(TAG, "Firebase configuration issue: $message")
            Toast.makeText(
                this, 
                "Firebase configuration issue: $message", 
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d(TAG, "Firebase configuration: $message")
        }
        
        // Also check network connectivity
        val hasNetwork = NetworkUtils.isNetworkAvailable(this)
        Log.d(TAG, "Network available: $hasNetwork")
        
        if (!hasNetwork) {
            Toast.makeText(
                this,
                "No internet connection. The app may not function properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun checkAndSetupSampleData() {
        Log.d(TAG, "Checking if sample data needs to be setup")
        try {
            // Check Firestore and set up sample data if needed
            val firestore = FirebaseFirestore.getInstance()
            
            // Check if users collection exists and has data
            firestore.collection("users").limit(1).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        Log.d(TAG, "Users collection exists and has data")
                    } else {
                        Log.w(TAG, "Users collection is empty")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to access Firestore", e)
                    
                    // If we can't access Firestore, show a more helpful message
                    val errorMessage = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        "Firestore is unavailable. Check your internet connection."
                    } else {
                        "Firestore access error: ${e.message}"
                    }
                    
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check sample data", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupNavigation() {
        // Initialize with HomeFragment
        if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
            navController.navigate(R.id.homeFragment)
        }
    }
    
    /**
     * Updates the bottom navigation appearance based on the background color of the current screen
     * @param isWhiteBackground true if the background is white, false if it's dark
     */
    private fun updateBottomNavAppearance(isWhiteBackground: Boolean) {
        val bottomNavContainer = findViewById<FrameLayout>(R.id.bottom_nav_container)
        
        if (isWhiteBackground) {
            // For white backgrounds, make the nav bar more visible with shadow and elevation
            val whiteElevation = resources.getDimension(R.dimen.nav_elevation_white_bg)
            bottomNavContainer.elevation = whiteElevation
        } else {
            // For dark backgrounds, keep it more subtle
            val darkElevation = resources.getDimension(R.dimen.nav_elevation_dark_bg)
            bottomNavContainer.elevation = darkElevation
        }
    }

    private fun showCreateGroupScreen() {
        navController.navigate(R.id.addGroupFragment)
    }

    private fun showRemoveGroupScreen() {
        navController.navigate(R.id.removeGroupFragment)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private var currentWindowInsets: WindowInsetsCompat? = null

    private fun setupWindowInsets() {
        val mainLayout = findViewById<View>(R.id.main)
        mainLayout?.let { layout ->
            ViewCompat.setOnApplyWindowInsetsListener(layout) { v, insets ->
                currentWindowInsets = insets
                applyPadding(v, insets, navController.currentDestination?.id)
                insets
            }
        }
        
        navController.addOnDestinationChangedListener { _, destination, _ ->
            mainLayout?.let { layout ->
                currentWindowInsets?.let { insets ->
                    applyPadding(layout, insets, destination.id)
                }
            }
        }
    }

    private fun applyPadding(v: View, insets: WindowInsetsCompat, destinationId: Int?) {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (destinationId == R.id.calendarFragment) {
            v.setPadding(0, 0, 0, systemBars.bottom)
        } else {
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
        }
    }

    private fun loadUserData() {
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Not logged in, redirect to login
            redirectToLogin()
            return
        } else {
            // Get user data from session
            val userData = sessionManager.getUserDetails()
            userType = userData[SessionManager.KEY_USER_TYPE] as String?
            userRoll = userData[SessionManager.KEY_USER_ROLL_NUMBER] as String?
            userYear = userData[SessionManager.KEY_USER_YEAR] as Int? ?: 0
            
            // Set up the UI immediately to show home screen first
            setupNavigation()
            
            // Log user details to help with debugging
            Log.d(TAG, "User data from session: type=$userType, roll=$userRoll, year=$userYear")
            
            // Additional checks for Firebase user
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                // Firebase Auth session expired, redirect to login
                Log.d(TAG, "Firebase user is null, redirecting to login")
                sessionManager.logoutUser()
                redirectToLogin()
                return
            } else {
                // Log Firebase user details for debugging
                Log.d(TAG, "Firebase user: uid=${firebaseUser.uid}, email=${firebaseUser.email}, displayName=${firebaseUser.displayName}")
                
                // If displayName contains userType and roll number, parse it
                firebaseUser.displayName?.let { displayName ->
                    if (displayName.contains(":")) {
                        val parts = displayName.split(":")
                        if (parts.size >= 2) {
                            val typeFromDisplay = parts[0]
                            val rollFromDisplay = parts[1]
                            
                            Log.d(TAG, "Firebase displayName parsed: type=$typeFromDisplay, roll=$rollFromDisplay")
                            
                            // If userType is null, use the one from displayName
                            if (userType.isNullOrEmpty()) {
                                userType = typeFromDisplay
                                Log.d(TAG, "Using userType from Firebase displayName: $userType")
                            }
                        }
                    }
                }
            }
        }
        
        // Ensure FCM wing/role topic subscriptions are always up to date
        (application as? ChatApplication)?.subscribeToUserTopics(sessionManager)
        
        // Firebase is already initialized in ChatApplication
        // Now just check if sample data needs to be set up
        checkAndSetupSampleData()
    }

    /**
     * Test connection to the secondary Firebase database
     * This is just for testing and can be removed in production
     */
    private fun testSecondaryFirebase() {
        val secondaryDb = MultiDatabaseHelper.getSecondaryFirestore()
        
        // Test write operation
        secondaryDb.collection("test")
            .document("test_document")
            .set(mapOf(
                "timestamp" to com.google.firebase.Timestamp.now(),
                "message" to "Test from ChatApp"
            ))
            .addOnSuccessListener {
                Log.d(TAG, "Successfully wrote to secondary Firebase")
                Toast.makeText(this, "Secondary Firebase connection successful", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to write to secondary Firebase", e)
                Toast.makeText(this, "Secondary Firebase connection failed", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Set up custom navigation buttons
     */
    private fun setupCustomNavigation() {
        // Find all navigation buttons
        val btnHome = findViewById<ImageButton>(R.id.btn_home)
        val btnChat = findViewById<ImageButton>(R.id.btn_chat)
        val btnCalendar = findViewById<ImageButton>(R.id.btn_calendar)
        val btnSchedule = findViewById<ImageButton>(R.id.btn_schedule)
        val btnProfile = findViewById<ImageButton>(R.id.btn_profile)

        // Determine admin from unified userType
        val isAdmin = sessionManager.fetchUserType().equals("Admin", ignoreCase = true)
        
        // Show/hide scheduling button based on admin status
        if (isAdmin) {
            btnSchedule.visibility = View.VISIBLE
        } else {
            btnSchedule.visibility = View.GONE
        }
        
        // Set initial selection
        btnHome.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        
        // Set click listeners
        btnHome.setOnClickListener {
            navController.navigate(R.id.homeFragment)
            resetNavButtonColors()
            btnHome.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        
        btnChat.setOnClickListener {
            navController.navigate(R.id.chatFragment)
            resetNavButtonColors()
            btnChat.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        
        btnCalendar.setOnClickListener {
            navController.navigate(R.id.calendarFragment)
            resetNavButtonColors()
            btnCalendar.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }

        // Explicitly show calendar button as it defaults to GONE in XML
        btnCalendar.visibility = View.VISIBLE
        
        btnSchedule.setOnClickListener {
            navController.navigate(R.id.schedulingFragment)
            resetNavButtonColors()
            btnSchedule.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }
        
        btnProfile.setOnClickListener {
            navController.navigate(R.id.profileFragment)
            resetNavButtonColors()
            btnProfile.setColorFilter(ContextCompat.getColor(this, R.color.blue))
        }

        // Interview feature removed
    }
    
    /**
     * Reset all nav button colors to white
     */
    private fun resetNavButtonColors() {
        findViewById<ImageButton>(R.id.btn_home).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_chat).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_calendar).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_schedule).clearColorFilter()
        findViewById<ImageButton>(R.id.btn_profile).clearColorFilter()
    }
}