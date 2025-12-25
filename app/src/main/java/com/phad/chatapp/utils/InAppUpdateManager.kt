package com.phad.chatapp.utils

import android.app.Activity
import android.util.Log
import com.phad.chatapp.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

object InAppUpdateManager {
    private const val TAG = "InAppUpdateManager"
    
    // Remote Config Keys
    private const val KEY_MIN_REQUIRED_VERSION = "minimum_required_version_code"
    private const val KEY_LATEST_VERSION = "latest_public_version_code"

    enum class UpdateStatus {
        MANDATORY,
        OPTIONAL,
        NONE
    }

    fun checkForUpdates(activity: Activity, onResult: (UpdateStatus) -> Unit) {
        val remoteConfig = Firebase.remoteConfig
        /*
         * Set settings for development:
         * For production, use a higher interval (e.g. 12 hours) to avoid throttling.
         * For testing, we use a small interval (0 or 3600).
         */
        val configSettings = remoteConfigSettings {
            // Set to 12 hours for production to avoid throttling
            minimumFetchIntervalInSeconds = 43200 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values (safe fallback)
        val defaults = mapOf(
            KEY_MIN_REQUIRED_VERSION to BuildConfig.VERSION_CODE - 1,
            KEY_LATEST_VERSION to BuildConfig.VERSION_CODE
        )
        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")
                } else {
                    Log.e(TAG, "Config fetch failed")
                }
                
                // Proceed regardless of fetch success (use cache or defaults)
                evaluateUpdateStatus(remoteConfig, onResult)
            }
    }

    private fun evaluateUpdateStatus(remoteConfig: FirebaseRemoteConfig, onResult: (UpdateStatus) -> Unit) {
        val currentVersion = BuildConfig.VERSION_CODE
        val minRequiredVersion = remoteConfig.getLong(KEY_MIN_REQUIRED_VERSION)
        val latestPublicVersion = remoteConfig.getLong(KEY_LATEST_VERSION)

        Log.d(TAG, "Current: $currentVersion, Min: $minRequiredVersion, Latest: $latestPublicVersion")

        when {
            currentVersion < minRequiredVersion -> {
                Log.i(TAG, "Mandatory update required.")
                onResult(UpdateStatus.MANDATORY)
            }
            currentVersion < latestPublicVersion -> {
                Log.i(TAG, "Optional update available.")
                onResult(UpdateStatus.OPTIONAL)
            }
            else -> {
                Log.i(TAG, "App is up to date.")
                onResult(UpdateStatus.NONE)
            }
        }
    }
}
