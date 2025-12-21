package com.phad.chatapp.utils

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object PlayStoreUpdateChecker {

    private const val TAG = "PlayStoreUpdateChecker"

    sealed class Result {
        object UpdateAvailable : Result()
        object NoUpdate : Result()
        data class Failure(val error: Throwable?) : Result()
    }

    fun checkForImmediateUpdate(activity: Activity, onResult: (Result) -> Unit) {
        val manager = AppUpdateManagerFactory.create(activity)
        val task = manager.appUpdateInfo

        task.addOnSuccessListener { info ->
            val updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            Log.d(TAG, "updateAvailable=$updateAvailable, immediateAllowed=$immediateAllowed")

            if (updateAvailable && immediateAllowed) {
                onResult(Result.UpdateAvailable)
            } else {
                onResult(Result.NoUpdate)
            }
        }.addOnFailureListener { error ->
            Log.w(TAG, "Failed to query update info", error)
            onResult(Result.Failure(error))
        }
    }
}


