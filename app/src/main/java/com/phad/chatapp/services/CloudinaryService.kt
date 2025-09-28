package com.phad.chatapp.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.phad.chatapp.utils.CloudinaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CloudinaryService(private val context: Context) {
    private val TAG = "CloudinaryService"

    /**
     * Initialize Cloudinary with configuration
     */
    fun initialize() {
        if (!CloudinaryConfig.isInitialized()) {
            CloudinaryConfig.init(context)
        }
    }

    /**
     * Upload an image to Cloudinary
     * @param imageUri The URI of the image to upload
     * @return Result containing the URL of the uploaded image or an error
     */
    suspend fun uploadImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!CloudinaryConfig.isInitialized()) {
                initialize()
            }
            
            Log.d(TAG, "Starting image upload: $imageUri")
            
            // Create a temporary file from the URI
            val tempFile = createTempFileFromUri(imageUri)
            if (tempFile == null) {
                Log.e(TAG, "Failed to create temporary file from URI")
                return@withContext Result.failure(Exception("Failed to create temporary file"))
            }
            
            // Upload the file to Cloudinary
            val imageUrl = suspendCoroutine<String> { continuation ->
                val requestId = MediaManager.get().upload(tempFile.absolutePath)
                    .option("folder", "attendance_images")
                    .option("public_id", "attendance_${UUID.randomUUID()}")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started: $requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100) / totalBytes
                            Log.d(TAG, "Upload progress: $progress%")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>?) {
                            val secureUrl = resultData?.get("secure_url") as? String
                            if (secureUrl != null) {
                                Log.d(TAG, "Upload successful: $secureUrl")
                                continuation.resume(secureUrl)
                            } else {
                                Log.e(TAG, "Upload successful but secure URL is null")
                                continuation.resumeWithException(Exception("Failed to get secure URL"))
                            }
                            
                            // Delete the temporary file
                            tempFile.delete()
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo?) {
                            Log.e(TAG, "Upload error: ${error?.description}")
                            continuation.resumeWithException(Exception(error?.description ?: "Unknown upload error"))
                            
                            // Delete the temporary file
                            tempFile.delete()
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo?) {
                            Log.d(TAG, "Upload rescheduled: ${error?.description}")
                        }
                    })
                    .dispatch()
            }
            
            return@withContext Result.success(imageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Create a temporary file from a URI
     */
    private suspend fun createTempFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            
            inputStream.close()
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temporary file", e)
            return@withContext null
        }
    }
} 