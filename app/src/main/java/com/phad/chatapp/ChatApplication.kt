package com.phad.chatapp

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.MultiDatabaseHelper
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
                
                // Removed diagnostics write to avoid creating collections/documents
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firestore", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
        }
    }
    
    /**
     * Initialize the secondary Firebase instance
     */
    private fun initializeSecondaryFirebase() {
        try {
            // Initialize the secondary Firebase app using our helper
            MultiDatabaseHelper.initializeSecondaryFirebase(this)
            
            // Avoid creating any diagnostics documents in secondary Firestore
            val secondaryDb = MultiDatabaseHelper.getSecondaryFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secondary Firebase", e)
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