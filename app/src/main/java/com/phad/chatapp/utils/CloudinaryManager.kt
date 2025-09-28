package com.phad.chatapp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CloudinaryManager {
    private const val TAG = "CloudinaryManager"

    fun init(context: Context) {
        // Use CloudinaryConfig for initialization
        if (!CloudinaryConfig.isInitialized()) {
            CloudinaryConfig.init(context)
        }
    }

    suspend fun uploadImage(imageUri: Uri): String = withContext(Dispatchers.IO) {
        if (!CloudinaryConfig.isInitialized()) {
            throw IllegalStateException("Cloudinary not initialized")
        }

        try {
            Log.d(TAG, "Starting image upload for URI: $imageUri")
            
            val result = suspendCancellableCoroutine<String> { continuation ->
                val requestId = MediaManager.get()
                    .upload(imageUri)
                    .option("folder", "attendance_images")
                    .option("resource_type", "image")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started for requestId: $requestId")
                        }

                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
                            Log.d(TAG, "Upload progress: $progress%")
                        }

                        override fun onSuccess(requestId: String, resultData: Map<Any?, Any?>) {
                            val url = resultData["secure_url"] as? String
                            if (url != null) {
                                Log.d(TAG, "Upload successful. URL: $url")
                                continuation.resume(url)
                            } else {
                                Log.e(TAG, "Upload successful but URL is null")
                                continuation.resumeWithException(Exception("Upload successful but URL is null"))
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "Upload failed: ${error.description}")
                            continuation.resumeWithException(Exception(error.description))
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.d(TAG, "Upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()

                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                }
            }

            Log.d(TAG, "Image upload completed successfully")
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            throw e
        }
    }
} 