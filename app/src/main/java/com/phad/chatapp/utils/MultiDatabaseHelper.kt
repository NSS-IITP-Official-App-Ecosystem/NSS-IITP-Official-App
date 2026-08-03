package com.phad.chatapp.utils

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context

/**
 * Helper class to manage multiple Firebase instances in one app
 */
class MultiDatabaseHelper {
    companion object {
        private const val TAG = "MultiDatabaseHelper"
        
        // App names for different Firebase instances
        const val PRIMARY_APP = "primary" // ChatApp (chatapp-24fae)
        const val SECONDARY_APP = "secondary" // TWApp (twapp-9bf5f)
        
        /**
         * Initialize the secondary Firebase app instance (TWApp)
         */
        fun initializeSecondaryFirebase(context: Context) {
            try {
                // Check if the app is already initialized
                FirebaseApp.getInstance(SECONDARY_APP)
                Log.d(TAG, "Secondary Firebase app (TWApp) already initialized")
            } catch (e: IllegalStateException) {
                // App doesn't exist yet, initialize it
                
                // TWApp Firebase project values
                val options = FirebaseOptions.Builder()
                    .setApiKey(com.phad.chatapp.BuildConfig.SECONDARY_FIREBASE_API_KEY)
                    .setApplicationId("1:295879966372:android:3d48ffc9aecf7a56d46d16")
                    .setProjectId("twapp-9bf5f")
                    .build()
                
                // Initialize the secondary app
                FirebaseApp.initializeApp(context, options, SECONDARY_APP)
                Log.d(TAG, "Secondary Firebase app (TWApp) initialized successfully")
            }
        }
        
        /**
         * Get Firestore instance for the primary database (ChatApp)
         */
        fun getPrimaryFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }
        
        /**
         * Get Firestore instance for the secondary database (TWApp)
         */
        fun getSecondaryFirestore(): FirebaseFirestore {
            val secondaryApp = FirebaseApp.getInstance(SECONDARY_APP)
            return FirebaseFirestore.getInstance(secondaryApp)
        }
        
        /**
         * Get the primary authentication instance (ChatApp)
         */
        fun getPrimaryAuth(): FirebaseAuth {
            return FirebaseAuth.getInstance()
        }
        
        /**
         * Get the secondary authentication instance (TWApp)
         */
        fun getSecondaryAuth(): FirebaseAuth {
            val secondaryApp = FirebaseApp.getInstance(SECONDARY_APP)
            return FirebaseAuth.getInstance(secondaryApp)
        }
    }
} 