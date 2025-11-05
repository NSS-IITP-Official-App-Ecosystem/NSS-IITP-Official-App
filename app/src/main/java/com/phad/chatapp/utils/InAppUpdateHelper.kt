package com.phad.chatapp.utils

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object InAppUpdateHelper {
    const val UPDATE_REQUEST_CODE = 9001
    private const val TAG = "InAppUpdateHelper"

    fun checkForImmediateUpdate(activity: Activity) {
        val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val isUpdateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isImmediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            Log.d(TAG, "Update available=$isUpdateAvailable, immediateAllowed=$isImmediateAllowed")

            if (isUpdateAvailable && isImmediateAllowed) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start update flow", e)
                }
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Failed to fetch update info", e)
        }
    }

    fun resumeIfUpdateInProgress(activity: Activity) {
        val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val developerTriggeredUpdateInProgress =
                appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

            if (developerTriggeredUpdateInProgress) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume update flow", e)
                }
            }
        }
    }
}


