package com.phad.chatapp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for uploading files to Cloudinary
 */
class CloudinaryHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "CloudinaryHelper"
        private var instance: CloudinaryHelper? = null
        
        @Volatile
        private var isInitialized = false
        
        /**
         * Initialize Cloudinary with credentials from resources
         * Call this once in Application onCreate or before first use
         */
        fun initialize(context: Context) {
            if (!isInitialized) {
                synchronized(this) {
                    if (!isInitialized) {
                        try {
                            // Get credentials from BuildConfig (loaded from local.properties)
                            val cloudName = com.phad.chatapp.BuildConfig.CLOUDINARY_CLOUD_NAME
                            val apiKey = com.phad.chatapp.BuildConfig.CLOUDINARY_API_KEY
                            val apiSecret = com.phad.chatapp.BuildConfig.CLOUDINARY_API_SECRET
                            
                            if (cloudName.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
                                throw IllegalStateException(
                                    "Cloudinary credentials not found. Please add them to local.properties:\n" +
                                    "cloudinary.cloud_name=YOUR_CLOUD_NAME\n" +
                                    "cloudinary.api_key=YOUR_API_KEY\n" +
                                    "cloudinary.api_secret=YOUR_API_SECRET"
                                )
                            }
                            
                            // Initialize MediaManager
                            val config = mapOf(
                                "cloud_name" to cloudName,
                                "api_key" to apiKey,
                                "api_secret" to apiSecret
                            )
                            
                            MediaManager.init(context, config)
                            isInitialized = true
                            Log.d(TAG, "Cloudinary initialized successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initialize Cloudinary", e)
                            throw IllegalStateException("Cloudinary initialization failed. Check credentials in local.properties", e)
                        }
                    }
                }
            }
        }
        
        fun getInstance(context: Context): CloudinaryHelper {
            if (!isInitialized) {
                initialize(context.applicationContext)
            }
            return instance ?: synchronized(this) {
                instance ?: CloudinaryHelper(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Upload an image to Cloudinary
     * @param uri Image URI from device
     * @param folder Folder path in Cloudinary (e.g., "updates/images")
     * @return Download URL of uploaded image
     */
    suspend fun uploadImage(uri: Uri, folder: String = "updates/images"): String {
        return uploadFile(uri, folder, "image")
    }
    
    /**
     * Upload a document to Cloudinary
     * @param uri Document URI from device
     * @param folder Folder path in Cloudinary (e.g., "updates/documents")
     * @return Download URL of uploaded document
     */
    suspend fun uploadDocument(uri: Uri, folder: String = "updates/documents"): String {
        return uploadFile(uri, folder, "raw")
    }
    
    /**
     * Generic file upload to Cloudinary
     * @param uri File URI
     * @param folder Cloudinary folder
     * @param resourceType "image", "video", or "raw" (for documents)
     * @return Secure HTTPS URL
     */
    private suspend fun uploadFile(uri: Uri, folder: String, resourceType: String): String =
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Starting upload: uri=$uri, folder=$folder, type=$resourceType")
                
                // Upload with Cloudinary MediaManager
                val requestId = MediaManager.get().upload(uri)
                    .option("folder", folder)
                    .option("resource_type", resourceType)
                    .unsigned("ml_default") // Upload preset configured in Cloudinary console
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started: requestId=$requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes.toFloat() / totalBytes * 100).toInt()
                            Log.d(TAG, "Upload progress: $progress%")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            try {
                                val secureUrl = resultData["secure_url"] as? String
                                if (secureUrl != null) {
                                    Log.d(TAG, "Upload successful: $secureUrl")
                                    if (continuation.isActive) {
                                        continuation.resume(secureUrl)
                                    }
                                } else {
                                    val error = "Upload succeeded but no URL returned"
                                    Log.e(TAG, error)
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(Exception(error))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing upload result", e)
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            val errorMsg = "Upload failed: ${error.description}"
                            Log.e(TAG, errorMsg)
                            if (continuation.isActive) {
                                continuation.resumeWithException(Exception(errorMsg))
                            }
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "Upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()
                
                // Handle cancellation
                continuation.invokeOnCancellation {
                    try {
                        MediaManager.get().cancelRequest(requestId)
                        Log.d(TAG, "Upload cancelled: $requestId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling upload", e)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start upload", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
}
