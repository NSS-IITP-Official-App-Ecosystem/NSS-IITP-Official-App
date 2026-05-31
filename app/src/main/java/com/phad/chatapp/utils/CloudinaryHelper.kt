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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.phad.chatapp.models.CloudinaryUploadResult

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
                                Log.e(TAG, "Cloudinary credentials not found in local.properties. Uploads will fail, but app will continue.")
                                return
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
     * @return CloudinaryUploadResult containing URL and Public ID
     */
    suspend fun uploadImage(uri: Uri, folder: String = "updates/images", onProgress: ((Int) -> Unit)? = null): CloudinaryUploadResult {
        return uploadFile(uri, folder, "image", onProgress)
    }
    
    /**
     * Upload a document to Cloudinary
     * @return CloudinaryUploadResult containing URL and Public ID
     */
    suspend fun uploadDocument(uri: Uri, folder: String = "updates/documents", onProgress: ((Int) -> Unit)? = null): CloudinaryUploadResult {
        return uploadFile(uri, folder, "raw", onProgress)
    }
    
    /**
     * Generic file upload to Cloudinary
     * @return CloudinaryUploadResult
     */
    private suspend fun uploadFile(uri: Uri, folder: String, resourceType: String, onProgress: ((Int) -> Unit)? = null): CloudinaryUploadResult =
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
                            onProgress?.invoke(progress)
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            try {
                                val secureUrl = resultData["secure_url"] as? String
                                val publicId = resultData["public_id"] as? String
                                
                                if (secureUrl != null && publicId != null) {
                                    Log.d(TAG, "Upload successful: $secureUrl, ID: $publicId")
                                    if (continuation.isActive) {
                                        continuation.resume(CloudinaryUploadResult(secureUrl, publicId))
                                    }
                                } else {
                                    val error = "Upload succeeded but URL or Public ID missing"
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

    /**
     * Delete an image from Cloudinary by URL (legacy/fallback)
     */
    suspend fun deleteImage(url: String): Boolean {
        return deleteMedia(url, "image")
    }

    /**
     * Delete an image from Cloudinary by Public ID
     */
    suspend fun deleteImageById(publicId: String): Boolean {
        return deleteMediaById(publicId, "image")
    }

    /**
     * Delete a document from Cloudinary by URL (legacy/fallback)
     */
    suspend fun deleteDocument(url: String): Boolean {
        return deleteMedia(url, "raw") 
    }

    /**
     * Delete a document from Cloudinary by Public ID
     */
    suspend fun deleteDocumentById(publicId: String): Boolean {
        return deleteMediaById(publicId, "raw")
    }
    
    // Original deleteMedia using URL parsing
    private suspend fun deleteMedia(url: String, resourceType: String): Boolean = withContext(Dispatchers.IO) {
        val publicId = getPublicIdFromUrl(url)
        if (publicId.isEmpty()) {
            Log.e(TAG, "Could not extract public ID from URL: $url")
            return@withContext false
        }
        return@withContext deleteMediaById(publicId, resourceType)
    }

    // New deleteMedia using direct Public ID
    private suspend fun deleteMediaById(publicId: String, resourceType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting to delete media. Public ID: $publicId, Type: $resourceType")

            // MediaManager wraps the Cloudinary instance
            // We need to use the uploader().destroy() method
            val result = MediaManager.get().cloudinary.uploader().destroy(publicId, mapOf(
                "resource_type" to resourceType,
                "invalidate" to true
            ))

            val resultStr = result["result"] as? String
            Log.d(TAG, "Delete result: $resultStr")
            return@withContext resultStr == "ok" || resultStr == "not found"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete media: ${e.message}", e)
            return@withContext false
        }
    }

    private fun getPublicIdFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: return ""
            // Path structure: /<cloud_name>/<resource_type>/upload/v<version>/<folder>/<filename>
            // We want <folder>/<filename_without_extension>
            
            // Split by '/'
            val parts = path.split("/")
            
            // Find the index of "upload"
            val uploadIndex = parts.indexOf("upload")
            if (uploadIndex == -1 || uploadIndex + 2 >= parts.size) return ""
            
            // The part after "upload" is version (v12345...), skip it
            // The rest is the public_id, but we need to remove file extension
            
            // Join parts after version
            val publicIdWithExtension = parts.subList(uploadIndex + 2, parts.size).joinToString("/")
            
            // Remove extension
            val lastDotIndex = publicIdWithExtension.lastIndexOf('.')
            if (lastDotIndex != -1) {
                publicIdWithExtension.substring(0, lastDotIndex)
            } else {
                publicIdWithExtension
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing public ID", e)
            ""
        }
    }
}
