package com.phad.chatapp

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
                val collectionName = if (loginType == "ADMIN") "NSS_ADMINS" else "Student"
                val documentSnapshot = db.collection(collectionName)
                    .document(rollNumber)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    if (documentSnapshot.exists()) {
                        val userData = documentSnapshot.data
                        if (loginType == "ADMIN") {
                            val collegeEmail = userData?.get("College_Email") as? String
                            if (!collegeEmail.isNullOrEmpty()) {
                                binding.rollNumberCheckMark.visibility = View.VISIBLE
                            } else {
                                binding.rollNumberCheckMark.visibility = View.GONE
                            }
                        } else {
                            // For students, check in Student collection
                            val firestoreEmail = userData?.get("Gmail_ID") as? String
                            if (!firestoreEmail.isNullOrEmpty()) {
                                binding.rollNumberCheckMark.visibility = View.VISIBLE
                            } else {
                                binding.rollNumberCheckMark.visibility = View.GONE
                            }
                        }
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
        val rollNumber = binding.etRollNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val collectionName = if (loginType == "ADMIN") "NSS_ADMINS" else "Student"
                val documentSnapshot = db.collection(collectionName)
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

                // For admin, use College_Email; for student, use Gmail_ID
                val firestoreEmail = if (loginType == "ADMIN") {
                    userData["College_Email"] as? String
                } else {
                    userData["Gmail_ID"] as? String
                }
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
                    val year = (userData["Year"] as? Long)?.toInt() ?: 1
                    val fullName = userData["Name"] as? String ?: ""
                    // Create login session
                    val finalUserType = if (loginType == "ADMIN") {
                        // For admins, we need to check the "users" collection to get the specific admin type
                        val userDocument = db.collection("users").document(rollNumber).get().await()
                        userDocument.getString("userType") ?: "Admin"
                    } else {
                        "Student"
                    }
                    sessionManager.createLoginSession(finalUserType, rollNumber, year)
                    sessionManager.saveUserName(fullName)
                    // Update FCM token (only for users collection)
                    if (loginType != "ADMIN") {
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
        return try {
            val document = db.collection("NSS_ADMINS").document(rollNumber).get().await()
            if (document.exists()) {
                val data = document.data!!
                ProfileUiState(
                    name = data["Name"] as? String ?: "Admin",
                    location = "N/A",
                    email = data["College_Email"] as? String ?: "",
                    phone = data["Contact_Number"] as? String ?: "",
                    rollNumber = data["Roll_Number"] as? String ?: rollNumber,
                    collegeEmail = data["College_Email"] as? String ?: "",
                    academicGroup = (data["Academic_Group"] as? Long)?.toString() ?: "N/A",
                    nssGroup = (data["NSS_Group"] as? Long)?.toString() ?: "N/A",
                    topic1 = "N/A",
                    topic2 = "N/A",
                    topic3 = "N/A",
                    userType = userType,
                    isStudent = false, // Admins are not students
                    Teaching_wing = data["Teaching_wing"] as? Boolean ?: false
                )
            } else {
                // Fallback to a default admin profile if not found
                ProfileUiState(
                    name = "Admin",
                    rollNumber = rollNumber,
                    userType = userType,
                    isStudent = false,
                    location = "N/A",
                    email = "N/A",
                    phone = "N/A",
                    collegeEmail = "N/A",
                    academicGroup = "N/A",
                    nssGroup = "N/A",
                    Teaching_wing = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching admin profile for $rollNumber", e)
            // Return a default profile on error
            ProfileUiState(
                name = "Admin",
                rollNumber = rollNumber,
                isStudent = false,
                location = "N/A",
                email = "N/A",
                phone = "N/A",
                collegeEmail = "N/A",
                academicGroup = "N/A",
                nssGroup = "N/A",
                Teaching_wing = false,
                userType = userType
            )
        }
    }

    private suspend fun fetchStudentProfile(rollNumber: String, userType: String): ProfileUiState {
        return try {
            val document = db.collection("Student").document(rollNumber).get().await()
            if (document.exists()) {
                val data = document.data!!
                ProfileUiState(
                    name = data["Name"] as? String ?: "Student",
                    location = data["location"] as? String ?: "N/A",
                    email = data["Gmail_ID"] as? String ?: "",
                    phone = data["Mobile_No_"] as? String ?: "",
                    rollNumber = data["Roll_No_"] as? String ?: rollNumber,
                    collegeEmail = data["Institute_ID"] as? String ?: "",
                    academicGroup = data["Academic_Grp"] as? String ?: "N/A",
                    nssGroup = data["NSS_gro"] as? String ?: "N/A",
                    topic1 = "N/A",
                    topic2 = "N/A",
                    topic3 = "N/A",
                    isStudent = true,
                    userType = userType,
                    Teaching_wing = data["Teaching_wing"] as? Boolean ?: false
                )
            } else {
                ProfileUiState(name = "Student", rollNumber = rollNumber, userType = userType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching student profile for $rollNumber", e)
            ProfileUiState(name = "Student", rollNumber = rollNumber, userType = userType)
        }
    }

    private fun handleForgotPassword() {
        val rollNumber = binding.etRollNumber.text.toString().trim()

        if (rollNumber.isEmpty()) {
            showToast("Please enter your roll number first")
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First check in the appropriate collection based on login type
                val collectionName = if (loginType == "ADMIN") "NSS_ADMINS" else "Student"
                val userDoc = db.collection(collectionName)
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

                // Get email from Firestore based on user type
                val firestoreEmail = if (loginType == "ADMIN") {
                    userDoc.getString("College_Email")
                } else {
                    userDoc.getString("Gmail_ID")
                }

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