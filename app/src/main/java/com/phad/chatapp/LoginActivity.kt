package com.phad.chatapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.phad.chatapp.models.Admin
import com.phad.chatapp.models.Student
import com.phad.chatapp.utils.Constants
import com.phad.chatapp.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestoreException
import com.phad.chatapp.databinding.ActivityLoginBinding
import com.phad.chatapp.R

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private val TAG = "LoginActivity"
    
    // UI components
    private lateinit var editTextRollNumber: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextPasskey: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonLoginFirstTime: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewStatus: TextView
    
    // Passkeys for user types
    private val STUDENT_PASSKEY = "9410408989"
    private val ADMIN1_PASSKEY = "9319308989"
    private val ADMIN2_PASSKEY = "9897209798"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)
        
        // Check if user is already logged in
        if (sessionManager.isLoggedIn()) {
            val lastChoice = sessionManager.getLastInterfaceChoice()
            if (lastChoice == "NSS") {
                val intent = Intent(this, com.phad.chatapp.NssMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            } else if (lastChoice == "TEACHING_WING") {
                val intent = Intent(this, com.phad.chatapp.MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            } else {
                // Default fallback: MainActivity (or show choice screen if you want)
                navigateToMainActivity()
                return
            }
        }
        
        // TEMPORARY DEBUG OPTION - Long press on the login screen to enable bypass
        // This is for emergency testing only when Firebase rules are causing issues
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnLongClickListener {
            showBypassDialog()
            true
        }
        
        // Set up click listeners for login buttons
        setupUI()
        
        // Keep references to existing UI components (they're hidden but we keep the references)
        editTextRollNumber = binding.editTextRollNumber
        editTextEmail = binding.editTextEmail
        editTextPassword = binding.editTextPassword
        editTextPasskey = binding.editTextPasskey
        buttonLogin = binding.buttonLogin
        buttonLoginFirstTime = binding.btnLoginFirstTime
        progressBar = binding.progressBar
        textViewStatus = binding.textViewStatus
    }
    
    private fun setupUI() {
        binding.btnLoginAsUser.setOnClickListener {
            val intent = Intent(this, LoginFormActivity::class.java)
            intent.putExtra("LOGIN_TYPE", "USER")
            startActivity(intent)
            overridePendingTransition(com.phad.chatapp.R.anim.slide_up, android.R.anim.fade_out)
        }

        binding.btnLoginAsAdmin.setOnClickListener {
            val intent = Intent(this, LoginFormActivity::class.java)
            intent.putExtra("LOGIN_TYPE", "ADMIN")
            startActivity(intent)
            overridePendingTransition(com.phad.chatapp.R.anim.slide_up, android.R.anim.fade_out)
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            buttonLogin.isEnabled = !isLoading
            editTextRollNumber.isEnabled = !isLoading
            editTextEmail.isEnabled = !isLoading
            editTextPassword.isEnabled = !isLoading
            editTextPasskey.isEnabled = !isLoading
        }
    }
    
    private fun performLogin(rollNumber: String, email: String, password: String, userType: String) {
        // This method is kept for backward compatibility
        // but is not used in the new UI flow
        showLoading(true)
        textViewStatus.text = "Authenticating..."
        
        // Normalize user type to proper case
        val normalizedUserType = when (userType.toUpperCase()) {
            "STUDENT" -> "Student"
            "ADMIN1" -> "Admin1"
            "ADMIN2" -> "Admin2"
            else -> userType
        }
        
        // Add detailed logging of credentials being used
        Log.d(TAG, "Login attempt - Email: $email, Roll: $rollNumber, UserType: $normalizedUserType")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First test Firebase connection before proceeding
                try {
                    Log.d(TAG, "Testing Firebase connection...")
                    val testDoc = firestore.collection("test_connection")
                        .document("test")
                        .get()
                        .await()
                    
                    Log.d(TAG, "Firebase connection test: ${if (testDoc.exists()) "Success" else "No test document, but connected"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase connection test failed", e)
                    showError("Firebase connection error: ${e.message}\nPlease check your internet connection and Firebase permissions.")
                    return@launch
                }
                
                // First check if user exists in Firestore with the provided roll number
                Log.d(TAG, "Checking for user in users collection with roll number: $rollNumber")
                
                try {
                    // Get the user document from Firestore
                    val documentSnapshot = firestore.collection("users")
                        .document(rollNumber)
                        .get()
                        .await()
                    
                    if (!documentSnapshot.exists()) {
                        Log.e(TAG, "User not found in users collection with roll number: $rollNumber")
                        showError("User not found with roll number: $rollNumber")
                        return@launch
                    }
                    
                    // User document exists, extract data
                    val userData = documentSnapshot.data
                    if (userData == null) {
                        Log.e(TAG, "User document exists but has no data")
                        showError("User data not found. Please contact administrator.")
                        return@launch
                    }
                    
                    // Verify that the email in Firestore matches the provided email
                    val firestoreEmail = userData["email"] as? String
                    if (firestoreEmail.isNullOrEmpty()) {
                        Log.e(TAG, "No email found in user document")
                        showError("Email not found in user data. Please contact administrator.")
                        return@launch
                    }
                    
                    if (firestoreEmail.trim().lowercase() != email.trim().lowercase()) {
                        Log.e(TAG, "Email mismatch. Provided: $email, In Firestore: $firestoreEmail")
                        showError("The provided email does not match the email registered with this roll number.")
                        return@launch
                    }
                    
                    // Extract the userType from Firestore
                    val firestoreUserType = userData["userType"] as? String ?: normalizedUserType
                    Log.d(TAG, "User found in Firestore with email: $firestoreEmail and type: $firestoreUserType")
                    
                    // Now attempt to authenticate with Firebase Auth
                    try {
                        // If user is already authenticated, verify the email
                        val currentUser = auth.currentUser
                        if (currentUser != null && currentUser.email == email) {
                            Log.d(TAG, "User is already authenticated: ${currentUser.email}")
                            
                            // Login success - extract additional data
                            val year = userData["year"] as? Long ?: 0L
                            
                            // Update FCM token and complete login
                            updateFCMTokenAndCompleteLogin(rollNumber, email, firestoreUserType, year.toInt())
                            return@launch
                        }
                        
                        // Try to sign in with Firebase Auth
                        Log.d(TAG, "Signing in with Firebase Auth: $email")
                        withContext(Dispatchers.Main) {
                            textViewStatus.text = "Signing in..."
                        }
                        
                        auth.signInWithEmailAndPassword(email, password).await()
                        Log.d(TAG, "Firebase Auth sign-in successful")
                        
                        // Force token refresh to ensure auth state is fully propagated
                        try {
                            val user = auth.currentUser
                            if (user != null) {
                                Log.d(TAG, "Getting fresh ID token...")
                                val tokenResult = user.getIdToken(true).await()
                                Log.d(TAG, "Token refreshed successfully, token valid: ${tokenResult.token != null}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error refreshing token", e)
                            // Continue anyway, might still work
                        }
                        
                        // Add a short delay to ensure Firebase auth state is fully propagated
                        try {
                            Log.d(TAG, "Adding short delay before database access")
                            kotlinx.coroutines.delay(1000)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during delay", e)
                        }
                        
                        // Implement emergency fallback for database access issues
                        val bypassSecurityRules = false  // Set to true ONLY for testing if nothing else works
                        if (bypassSecurityRules) {
                            Log.w(TAG, "⚠️ USING SECURITY BYPASS! This should only be used for testing")
                            val userYearValue = userData["year"] as? Long ?: 0L  // Get year from userData
                            sessionManager.createLoginSession(firestoreUserType, rollNumber, userYearValue.toInt())
                            redirectToMain()
                            return@launch
                        }
                        
                        // Extract additional data for session
                        val userYearValue = userData["year"] as? Long ?: 0L
                        
                        // Update FCM token and complete login
                        updateFCMTokenAndCompleteLogin(rollNumber, email, firestoreUserType, userYearValue.toInt())
                        
                    } catch (e: FirebaseAuthInvalidUserException) {
                        Log.e(TAG, "Firebase Auth error: User not found", e)
                        showError("Authentication failed: Email not registered in Firebase Auth.")
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        Log.e(TAG, "Firebase Auth error: Invalid password", e)
                        showError("Authentication failed: Invalid password. Double-check your password.")
                    } catch (e: FirebaseNetworkException) {
                        Log.e(TAG, "Firebase Auth error: Network error", e)
                        showError("Authentication failed: Network error. Please check your connection.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Firebase Auth error: ${e.javaClass.simpleName}", e)
                        showError("Authentication failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    when (e) {
                        is FirebaseFirestoreException -> {
                            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Log.e(TAG, "Firebase permission denied", e)
                                showError("Permission denied: Firebase security rules are preventing access.\n\n" +
                                    "This is usually a server-side configuration issue. Please contact the administrator.")
                            } else {
                                Log.e(TAG, "Firestore error", e)
                                showError("Database error: ${e.message}")
                            }
                        }
                        is FirebaseNetworkException -> {
                            Log.e(TAG, "Network error", e)
                            showError("Network error: Please check your internet connection")
                        }
                        else -> {
                            Log.e(TAG, "Error retrieving user data", e)
                            showError("Error: ${e.message}")
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in login flow", e)
                showError("Unexpected error: ${e.message}")
            }
        }
    }
    
    private fun redirectToMain() {
        // After successful login, save FCM token and then redirect
        saveFcmTokenToUserDocument(sessionManager.fetchUserId())
        
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Save FCM token to the user's document in Firestore
     */
    private fun saveFcmTokenToUserDocument(userRollNumber: String) {
        if (userRollNumber.isEmpty()) return
        
        // Request notification permissions if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
        
        // Get the FCM token and update the user document
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }
            
            // Get token
            val token = task.result
            
            // Update Firestore document with token
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userRollNumber)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated for user $userRollNumber")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update FCM token", e)
                }
        }
    }
    
    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            showLoading(false)
            textViewStatus.text = message
            textViewStatus.setTextColor(resources.getColor(android.R.color.holo_red_light))
            textViewStatus.visibility = View.VISIBLE
            
            // Log the error message for debugging
            Log.e(TAG, "Login Error: $message")
            
            // Show toast for better visibility
            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun updateFCMTokenAndCompleteLogin(userId: String, email: String, userType: String, year: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get FCM token
                val token = FirebaseMessaging.getInstance().token.await()
                
                // Update token in Firestore
                firestore.collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .await()
                
                Log.d(TAG, "FCM token updated successfully for user $userId")
                
                // Add user to the announcement group
                addUserToAnnouncementGroup(userId)
                
                // Add roll number to Firebase user's display name for easier identification
                try {
                    auth.currentUser?.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName("$userType|$userId")
                            .build()
                    )?.await()
                    Log.d(TAG, "Updated Firebase user profile with roll number")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update Firebase user profile", e)
                }
                
                // Complete login and redirect
                withContext(Dispatchers.Main) {
                    sessionManager.createLoginSession(userType, userId, year)
                    redirectToMain()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token", e)
                
                // Still complete login even if FCM token update fails
                withContext(Dispatchers.Main) {
                    sessionManager.createLoginSession(userType, userId, year)
                    redirectToMain()
                }
            }
        }
    }
    
    /**
     * Add the user to the system-wide announcement group
     */
    private fun addUserToAnnouncementGroup(userId: String) {
        try {
            Constants.addUserToAnnouncementGroup(userId) { success ->
                if (success) {
                    Log.d(TAG, "User $userId added to Announcement group")
                } else {
                    Log.e(TAG, "Failed to add user $userId to Announcement group")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding user to Announcement group", e)
        }
    }
    
    // Add this method to show the bypass dialog
    private fun showBypassDialog() {
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Debug Option")
            .setMessage("This is a developer option to bypass Firebase authentication for testing. Use ONLY if you're experiencing permission issues with Firebase.")
            .setPositiveButton("Enable Bypass") { _, _ ->
                sessionManager.enableFirebaseAuthBypass(true)
                Toast.makeText(this, "Authentication bypass enabled", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        alertDialog.show()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
} 