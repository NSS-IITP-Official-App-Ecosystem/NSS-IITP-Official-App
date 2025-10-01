package com.phad.chatapp

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.MultiDatabaseHelper
import com.phad.chatapp.utils.CloudinaryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.phad.chatapp.utils.Constants
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

class ChatApplication : Application() {
    private val TAG = "ChatApplication"
    
    companion object {
        lateinit var instance: ChatApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Apply dark mode based on system setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        // Initialize Cloudinary
        initializeCloudinary()
        
        // Initialize Firebase
        try {
            Log.d(TAG, "Initializing Firebase...")
            
            // Check if Firebase is already initialized
            var app: FirebaseApp? = null
            try {
                app = FirebaseApp.getInstance()
                Log.d(TAG, "Firebase already initialized: ${app.name}")
            } catch (e: IllegalStateException) {
                // Firebase not initialized yet, initialize it
                Log.d(TAG, "Firebase not initialized yet, initializing now")
                FirebaseApp.initializeApp(this)
                app = FirebaseApp.getInstance()
            }
            
            Log.d(TAG, "Firebase info: Name=${app?.name}, Options=${app?.options?.applicationId}")
            
            // Initialize the secondary Firebase app
            initializeSecondaryFirebase()
            
            // Initialize FCM
            initializeFCM()
            
            // Test Firestore access permissions
            // Removed test connection diagnostics
            
            // Initialize system announcement group
            initializeAnnouncementGroup()
            
            // Test Firestore initialization
            try {
                val db = FirebaseFirestore.getInstance()
                
                // Enable disk persistence
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                db.firestoreSettings = settings
                
                Log.d(TAG, "Firestore initialized successfully")
                
                // Get app version using PackageManager
                val packageInfo = try {
                    packageManager.getPackageInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                val versionName = packageInfo?.versionName ?: "unknown"
                
                // Add a test document to verify write capabilities
                db.collection("diagnostics")
                    .document("init_test")
                    .set(mapOf(
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        "app_version" to versionName
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "Firestore diagnostic write successful")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore diagnostic write failed", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firestore", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
        }
    }
    
    /**
     * Initialize Cloudinary for image uploads
     */
    private fun initializeCloudinary() {
        try {
            val success = CloudinaryConfig.init(this)
            if (success) {
                Log.d(TAG, "Cloudinary initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize Cloudinary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Cloudinary", e)
        }
    }
    
    /**
     * Initialize the secondary Firebase instance
     */
    private fun initializeSecondaryFirebase() {
        try {
            // Initialize the secondary Firebase app using our helper
            MultiDatabaseHelper.initializeSecondaryFirebase(this)
            
            // Test connection to secondary Firestore
            val secondaryDb = MultiDatabaseHelper.getSecondaryFirestore()
            secondaryDb.collection("diagnostics")
                .document("init_test")
                .set(mapOf(
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "message" to "Secondary Firestore initialized successfully"
                ))
                .addOnSuccessListener {
                    Log.d(TAG, "Secondary Firestore diagnostic write successful")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Secondary Firestore diagnostic write failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secondary Firebase", e)
        }
    }
    
    /**
     * Initialize Firebase Cloud Messaging
     */
    private fun initializeFCM() {
        try {
            // Get the FCM token
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM Token: $token")
                    saveTokenToFirestore(token)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to retrieve FCM token", e)
                }
            
            // Subscribe to a default topic for global announcements
            FirebaseMessaging.getInstance().subscribeToTopic("global")
                .addOnSuccessListener {
                    Log.d(TAG, "Subscribed to 'global' FCM topic")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to subscribe to 'global' FCM topic", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM", e)
        }
    }
    
    /**
     * Save FCM token to the user's Firestore document
     */
    private fun saveTokenToFirestore(token: String) {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.fetchUserId()
        
        if (userId.isEmpty()) {
            Log.d(TAG, "User not logged in yet, token will be saved after login")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated in Firestore for user $userId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update FCM token in Firestore", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving FCM token to Firestore", e)
            }
        }
    }
    
    /**
     * Ensure the system Announcement group exists
     */
    private fun initializeAnnouncementGroup() {
        try {
            Log.d(TAG, "Initializing Announcement group...")
            val groupRepository = GroupRepository()
            
            CoroutineScope(Dispatchers.IO).launch {
                Constants.getOrCreateAnnouncementGroup { announcementGroupId ->
                    // Use the announcement group ID
                    if (Constants.exists(announcementGroupId)) {
                        Log.d(TAG, "Announcement group initialized successfully")
                    } else {
                        Log.e(TAG, "Failed to initialize Announcement group")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Announcement group", e)
        }
    }
    
    /**
     * Test Firestore access permissions
     */
    // Removed test connection diagnostics
    
    /**
     * Alternative method to test Firestore access using the users collection
     */
    private fun tryAlternativeFirestoreTest(db: FirebaseFirestore, testData: Map<String, Any>) {
        try {
            // Try method 2: Direct access to the users collection
            Log.d(TAG, "Trying alternative Firestore test (method 2)")
            
            // Just try to list users first without writing
            db.collection("users")
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        Log.d(TAG, "Users collection is empty or inaccessible")
                    } else {
                        Log.d(TAG, "Successfully read from users collection")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to read from users collection", e)
                    
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.e(TAG, "SEVERE PERMISSION ISSUE: Firebase security rules are preventing database access")
                        Log.e(TAG, "Please check the Firebase Console and update security rules")
                    }
                }
            
            // Method 3: Try to access a public collection
            // This collection might be accessible even with strict rules
            db.collection("public_data")
                .document("app_status")
                .set(mapOf("last_startup" to com.google.firebase.Timestamp.now()))
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully wrote to public_data collection")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to write to public_data collection", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternative Firestore test", e)
        }
    }
}

/**
 * Custom Glide module for app-specific configurations
 */
@GlideModule
class ChatAppGlideModule : AppGlideModule() {
    
    override fun registerComponents(context: android.content.Context, glide: com.bumptech.glide.Glide, registry: com.bumptech.glide.Registry) {
        // Create custom OkHttp client with increased timeouts for Drive images
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
        
        // Replace the default HttpURLConnection with OkHttp
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(client))
    }
} 