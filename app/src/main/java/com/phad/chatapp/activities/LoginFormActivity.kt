package com.phad.chatapp.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.databinding.ActivityLoginFormBinding
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.NssMainActivity
import com.phad.chatapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.graphics.Rect
import android.view.ViewGroup
import com.phad.chatapp.ui.profile.ProfileUiState
import com.phad.chatapp.utils.AttendanceStatsUpdater

class LoginFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginFormBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private val TAG = "LoginFormActivity"
    
    private var loginType: String = "USER"
    private var isPasswordVisible = false
    private var originalBottomCurveY = 0f

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
                val documentSnapshot = db.collection("users")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)
        
        // Get login type from intent
        loginType = intent.getStringExtra("LOGIN_TYPE") ?: "USER"

        setupUI()
        setupKeyboardBehavior()
    }

    private fun setupKeyboardBehavior() {
        // Store the original Y position of the bottom curve
        binding.bottomCurve.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                originalBottomCurveY = binding.bottomCurve.y
                binding.bottomCurve.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        // Add keyboard listener
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - r.bottom

            // If keyboard is showing
            if (keypadHeight > screenHeight * 0.15) {
                // Calculate the amount to shift up
                val shiftAmount = keypadHeight.toFloat()
                // Shift the bottom curve and its contents up
                binding.bottomCurve.animate()
                    .translationY(-shiftAmount)
                    .setDuration(200)
                    .start()
            } else {
                // Reset position when keyboard is hidden
                binding.bottomCurve.animate()
                    .translationY(0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun setupUI() {
        // Set login type text
        binding.textViewLoginType.text = if (loginType == "ADMIN") "As Admin" else "As User"
        
        // Back button click listener
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, com.phad.chatapp.R.anim.slide_down)
        }
        
        // Password visibility toggle
        binding.passwordVisibilityToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // Show password
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.passwordVisibilityToggle.alpha = 1.0f
            } else {
                // Hide password
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.passwordVisibilityToggle.alpha = 0.6f
            }
            // Move cursor to end
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        // Roll number focus change listener
        // Remove the roll number focus change listener that triggers autofill
        // binding.etRollNumber.setOnFocusChangeListener { _, hasFocus -> ... }
        
        // Login button click listener
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                val userType = if (loginType == "ADMIN") "Admin" else "Student"
                performLogin(userType)
            }
        }
        
        // Forgot password listener
        binding.textViewForgotPassword.setOnClickListener {
            handleForgotPassword()
        }
    }

    private fun validateInputs(): Boolean {
        val rollNumber = binding.etRollNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (rollNumber.isEmpty()) {
            showToast("Roll number is required")
            return false
        }

        if (password.isEmpty()) {
            showToast("Password is required")
            return false
        }

        return true
    }

    private fun checkUserWithRollNumber(rollNumber: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rollNumberCheckMark.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the correct case-insensitive roll number
                val correctRollNumber = findCorrectRollNumberCase(rollNumber)

                withContext(Dispatchers.Main) {
                    if (correctRollNumber != null) {
                        binding.rollNumberCheckMark.visibility = View.VISIBLE
                        binding.progressBar.visibility = View.GONE
                    } else {
                        binding.progressBar.visibility = View.GONE
                        showToast("User not found with this roll number")
                        binding.rollNumberCheckMark.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error checking roll number", e)
                    showToast("Error: ${e.message}")
                    binding.rollNumberCheckMark.visibility = View.GONE
                }
            }
        }
    }

    private fun performLogin(userType: String) {
        val inputRollNumber = binding.etRollNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the correct case-insensitive roll number
                val correctRollNumber = findCorrectRollNumberCase(inputRollNumber)
                
                if (correctRollNumber == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("User not found with this roll number")
                    }
                    return@launch
                }

                // Use the correct roll number from database for further operations
                val rollNumber = correctRollNumber
                
                // New unified users collection lookup by rollNumber (document id)
                val documentSnapshot = db.collection("users")
                    .document(rollNumber)
                    .get()
                    .await()

                if (!documentSnapshot.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("User not found with this roll number")
                    }
                    return@launch
                }

                // Get user data
                val userData = documentSnapshot.data
                if (userData == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("User data not found. Please contact administrator.")
                    }
                    return@launch
                }

                // VALIDATE ROLE MATCH
                val firestoreUserTypeRaw = userData["userType"] as? String ?: "Student"
                val isFirestoreAdmin = firestoreUserTypeRaw.equals("Admin", ignoreCase = true)
                val isLoginAsAdmin = loginType == "ADMIN"

                if (isLoginAsAdmin && !isFirestoreAdmin) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Access Denied: You are not an Admin.")
                    }
                    return@launch
                }

                if (!isLoginAsAdmin && isFirestoreAdmin) {
                     withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Access Denied: Please login using 'Login as Admin'.")
                    }
                    return@launch
                }

                // Use instituteOutlookId for both admin and student
                val firestoreEmail = userData["instituteOutlookId"] as? String
                if (firestoreEmail.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Email not found in user data. Please contact administrator.")
                    }
                    return@launch
                }

                // Now attempt to authenticate with Firebase Auth using the retrieved email
                try {
                    auth.signInWithEmailAndPassword(firestoreEmail, password).await()
                    // Login successful - extract user data
                    val fullName = userData["name"] as? String ?: ""
                    // Create login session
                    val finalUserType = (userData["userType"] as? String ?: "student").replaceFirstChar { it.lowercase() }
                    sessionManager.createLoginSession(if (finalUserType == "admin") "Admin" else "Student", rollNumber, 0)
                    sessionManager.saveUserName(fullName)
                    // Update FCM token (only for users collection)
                    if (true) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        try {
                            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                            db.collection("users")
                                .document(rollNumber)
                                .update("fcmToken", token)
                                .await()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating FCM token", e)
                            // Continue anyway
                        }
                    }
                    }
                    // Now fetch full profile and save to session
                    fetchProfileAndProceed(rollNumber, finalUserType)
                    // Default interface to NSS after unified login
                    SessionManager(this@LoginFormActivity).setLastInterfaceChoice("NSS")
                    // Also update attendance stats in session after login
                    CoroutineScope(Dispatchers.IO).launch {
                        AttendanceStatsUpdater.updateAttendanceStatsInSession(this@LoginFormActivity)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        when (e) {
                            is FirebaseAuthInvalidUserException -> 
                                showToast("Authentication failed: Email not registered")
                            is FirebaseAuthInvalidCredentialsException -> 
                                showToast("Authentication failed: Invalid password")
                            else -> 
                                showToast("Authentication failed: ${e.message}")
                        }
                        Log.e(TAG, "Authentication error", e)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Error: ${e.message}")
                    Log.e(TAG, "Login error", e)
                }
            }
        }
    }

    private fun fetchProfileAndProceed(rollNumber: String, userType: String) {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = when {
                    userType.startsWith("Admin") -> fetchAdminProfile(rollNumber, userType)
                    else -> fetchStudentProfile(rollNumber, userType)
                }

                // Save complete profile to session
                sessionManager.createProfileSession(profile)

                // Attempt silent device binding (NULL policy on server)
                try {
                    val roll = rollNumber
                    val nonce = com.phad.chatapp.network.BackendApi.getBindChallenge(roll)
                    val pubPem = com.phad.chatapp.security.SecurityKeyManager.getPublicKeyPem()
                    val sig = com.phad.chatapp.security.SecurityKeyManager.signBase64(nonce)
                    com.phad.chatapp.network.BackendApi.bindDevice(roll, pubPem, sig)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Device bind attempt skipped: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val isTeachingWing = (profile as? com.phad.chatapp.ui.profile.ProfileUiState)?.Teaching_wing ?: false
                    if (isTeachingWing) {
                        // Show the interface selection screen for all teaching wing users (admin or student)
                        val intent = Intent(this@LoginFormActivity, com.phad.chatapp.ui.profile.NssOrTeachingWingActivity::class.java)
                        intent.putExtra("teaching_wing", true)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Go directly to NSS home for non-teaching users
                        val intent = Intent(this@LoginFormActivity, NssMainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error fetching profile", e)
                    showToast("Failed to load profile: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchAdminProfile(rollNumber: String, userType: String): ProfileUiState {
        val doc = db.collection("users").document(rollNumber).get().await()
        val name = doc.getString("name") ?: "Admin"
        val email = doc.getString("instituteOutlookId") ?: ""
        // Determine Teaching Wing from `wings` array in users doc
        val wings = doc.get("wings") as? List<*> ?: emptyList<Any>()
        val isTeachingWing = wings.any { (it as? String)?.equals("Teaching and Technical Wing", ignoreCase = true) == true }
        return ProfileUiState(
            name = name,
            location = "N/A",
            email = email,
            phone = "",
            rollNumber = rollNumber,
            collegeEmail = email,
            academicGroup = "N/A",
            nssGroup = "N/A",
            topic1 = "N/A",
            topic2 = "N/A",
            topic3 = "N/A",
            userType = userType,
            isStudent = false,
            Teaching_wing = isTeachingWing
        )
    }

    private suspend fun fetchStudentProfile(rollNumber: String, userType: String): ProfileUiState {
        val doc = db.collection("users").document(rollNumber).get().await()
        val name = doc.getString("name") ?: "Student"
        val email = doc.getString("instituteOutlookId") ?: ""
        // Determine Teaching Wing from `wings` array in users doc
        val wings = doc.get("wings") as? List<*> ?: emptyList<Any>()
        val isTeachingWing = wings.any { (it as? String)?.equals("Teaching and Technical Wing", ignoreCase = true) == true }
        return ProfileUiState(
            name = name,
            location = "N/A",
            email = email,
            phone = "",
            rollNumber = rollNumber,
            collegeEmail = email,
            academicGroup = "N/A",
            nssGroup = "N/A",
            topic1 = "N/A",
            topic2 = "N/A",
            topic3 = "N/A",
            isStudent = true,
            userType = userType,
            Teaching_wing = isTeachingWing
        )
    }

    private fun handleForgotPassword() {
        val inputRollNumber = binding.etRollNumber.text.toString().trim()

        if (inputRollNumber.isEmpty()) {
            showToast("Please enter your roll number first")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the correct case-insensitive roll number
                val correctRollNumber = findCorrectRollNumberCase(inputRollNumber)
                
                if (correctRollNumber == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("User not found with this roll number")
                    }
                    return@launch
                }

                // Use the correct roll number from database
                val rollNumber = correctRollNumber
                
                // Look up unified users collection for email
                val userDoc = db.collection("users")
                    .document(rollNumber)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("User not found with this roll number")
                    }
                    return@launch
                }

                // Get email from Firestore unified field
                val firestoreEmail = userDoc.getString("instituteOutlookId")

                if (firestoreEmail.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Email not found in user data. Please contact administrator.")
                    }
                    return@launch
                }

                // Send password reset email
                try {
                    auth.sendPasswordResetEmail(firestoreEmail).await()
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Password reset email sent to associated email")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Failed to send password reset email: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Error: ${e.message}")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, com.phad.chatapp.R.anim.slide_down)
    }
} 