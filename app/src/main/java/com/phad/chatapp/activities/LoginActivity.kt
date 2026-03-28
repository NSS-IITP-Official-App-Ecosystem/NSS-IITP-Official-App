package com.phad.chatapp.activities

import android.content.Intent


import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

import com.phad.chatapp.BuildConfig
import com.phad.chatapp.models.Admin
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
import com.phad.chatapp.MainActivity
import com.phad.chatapp.NssMainActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private val TAG = "LoginActivity"
    
    /**
     * Generate all possible case combinations for a roll number in NNNNAANN format
     * For example: 2301CS06 -> [2301CS06, 2301Cs06, 2301cS06, 2301cs06]
     */
    private fun generateRollNumberCaseCombinations(rollNumber: String): List<String> {
        if (rollNumber.length != 8) return listOf(rollNumber)
        
        val digits = rollNumber.substring(0, 4) // First 4 digits
        val letters = rollNumber.substring(4, 6) // Two letters
        val lastDigits = rollNumber.substring(6, 8) // Last 2 digits
        
        val combinations = mutableListOf<String>()
        
        // Generate all 4 combinations of the two letters
        val letter1 = letters[0]
        val letter2 = letters[1]
        
        combinations.add("$digits${letter1.uppercase()}${letter2.uppercase()}$lastDigits") // CS
        combinations.add("$digits${letter1.uppercase()}${letter2.lowercase()}$lastDigits") // Cs
        combinations.add("$digits${letter1.lowercase()}${letter2.uppercase()}$lastDigits") // cS
        combinations.add("$digits${letter1.lowercase()}${letter2.lowercase()}$lastDigits") // cs
        
        return combinations
    }

    /**
     * Find the correct roll number case in the database
     * Returns the actual roll number from database if found, null otherwise
     */
    private suspend fun findCorrectRollNumberCase(inputRollNumber: String): String? {
        val combinations = generateRollNumberCaseCombinations(inputRollNumber)
        
        for (combination in combinations) {
            try {
                val documentSnapshot = firestore.collection("users")
                    .document(combination)
                    .get()
                    .await()
                
                if (documentSnapshot.exists()) {
                    return combination // Return the actual roll number from database
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking roll number combination: $combination", e)
            }
        }
        
        return null // No valid combination found
    }
    
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
                val intent = Intent(this, NssMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            } else if (lastChoice == "TEACHING_WING") {
                val intent = Intent(this, MainActivity::class.java)
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
        
        // DEBUG OPTION - Only available in debug builds
        // Long press on the login screen to enable bypass (for testing only)
        if (BuildConfig.DEBUG) {
            val rootView = findViewById<View>(android.R.id.content)
            rootView.setOnLongClickListener {
                showBypassDialog()
                true
            }
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
        
        val inputRollNumber = rollNumber.trim()
        
        // Normalize user type to app's canonical values
        val normalizedUserType = when (userType.uppercase()) {
            "STUDENT" -> "Student"
            "ADMIN", "ADMIN1", "ADMIN2" -> "Admin"
            else -> userType
        }
        
        // Add detailed logging of credentials being used
        Log.d(TAG, "Login attempt - Email: $email, Roll: $inputRollNumber, UserType: $normalizedUserType")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the correct case-insensitive roll number
                val correctRollNumber = findCorrectRollNumberCase(inputRollNumber)
                
                if (correctRollNumber == null) {
                    Log.e(TAG, "User not found in users collection with roll number: $inputRollNumber")
                    showError("User not found with roll number: $inputRollNumber")
                    return@launch
                }
                
                // Use the correct roll number from database for further operations
                val actualRollNumber = correctRollNumber
                Log.d(TAG, "Found correct roll number case: $actualRollNumber")
                
                try {
                    // Get the user document from Firestore
                    val documentSnapshot = firestore.collection("users")
                        .document(actualRollNumber)
                        .get()
                        .await()
                    
                    if (!documentSnapshot.exists()) {
                        Log.e(TAG, "User not found in users collection with roll number: $actualRollNumber")
                        showError("User not found with roll number: $actualRollNumber")
                        return@launch
                    }
                    
                    // User document exists, extract data
                    val userData = documentSnapshot.data
                    if (userData == null) {
                        Log.e(TAG, "User document exists but has no data")
                        showError("User data not found. Please contact administrator.")
                        return@launch
                    }
                    
                    // Verify that the email in Firestore matches the provided email (instituteOutlookId)
                    val firestoreEmail = userData["instituteOutlookId"] as? String
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
                    val firestoreUserTypeRaw = userData["userType"] as? String ?: normalizedUserType
                    val firestoreUserType = if (firestoreUserTypeRaw.equals("admin", ignoreCase = true)) "Admin" else "Student"
                    Log.d(TAG, "User found in Firestore with email: $firestoreEmail and type: $firestoreUserType")
                    
                    // Now attempt to authenticate with Firebase Auth
                    try {
                        // If user is already authenticated, verify the email
                        val currentUser = auth.currentUser
                        if (currentUser != null && currentUser.email == email) {
                            Log.d(TAG, "User is already authenticated: ${currentUser.email}")
                            
                            // Complete login
                            addUserToAnnouncementGroup(actualRollNumber)
                            withContext(Dispatchers.Main) {
                                val year = 0L
                                sessionManager.createLoginSession(firestoreUserType, actualRollNumber, year.toInt())
                                redirectToMain()
                            }
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
                            sessionManager.createLoginSession(firestoreUserType, actualRollNumber, userYearValue.toInt())
                            redirectToMain()
                            return@launch
                        }
                        
                        // Complete login
                        addUserToAnnouncementGroup(actualRollNumber)
                        
                        // Add roll number to Firebase user's display name
                        try {
                            auth.currentUser?.updateProfile(
                                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName("$firestoreUserType|$actualRollNumber")
                                    .build()
                            )?.await()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update Firebase user profile", e)
                        }
                        
                        withContext(Dispatchers.Main) {
                            val userYearValue = 0L
                            sessionManager.createLoginSession(firestoreUserType, actualRollNumber, userYearValue.toInt())
                            redirectToMain()
                        }
                        
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
    
    private fun showError(message: String) {
        runOnUiThread {
            showLoading(false)
            textViewStatus.text = message
            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun redirectToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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