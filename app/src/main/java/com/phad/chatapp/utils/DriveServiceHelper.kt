package com.phad.chatapp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Helper class for Google Drive operations using a service account
 */
class DriveServiceHelper(private val context: Context) {
    companion object {
        private const val TAG = "DriveServiceHelper"
        private const val APPLICATION_NAME = "ChatApp"
        private const val SERVICE_ACCOUNT_FILE = "service-account.json"
        
        // Folder ID from the shared Google Drive folder
        // This ID is extracted from the URL: https://drive.google.com/drive/folders/1GZT5G8CrG4Gqiiw9qyBKIkT6SUqJi1F6
        const val ROOT_FOLDER_ID = "1GZT5G8CrG4Gqiiw9qyBKIkT6SUqJi1F6"
        
        // Folder IDs for different media types (will be created if they don't exist)
        private var IMAGES_FOLDER_ID: String? = null
        private var DOCUMENTS_FOLDER_ID: String? = null
        
        // Singleton instance
        @Volatile
        private var instance: DriveServiceHelper? = null
        
        fun getInstance(context: Context): DriveServiceHelper {
            return instance ?: synchronized(this) {
                instance ?: DriveServiceHelper(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Drive service initialized with service account credentials
    private val driveService: Drive by lazy {
        val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        
        // Load credentials from the service account file
        val credentials = context.assets.open(SERVICE_ACCOUNT_FILE).use { inputStream ->
            GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf(DriveScopes.DRIVE_FILE))
        }
        
        Drive.Builder(
            httpTransport,
            jsonFactory,
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }
    
    /**
     * Initialize media folders (Obsolete stub)
     */
    suspend fun initFolders() = withContext(Dispatchers.IO) {
        Log.d(TAG, "initFolders called (no-op stub)")
    }
    
    /**
     * Upload a file to Google Drive (Obsolete stub - uploads use Cloudinary)
     */
    suspend fun uploadFile(
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        fileType: FileTypeEnum
    ): Result<String> = withContext(Dispatchers.IO) {
        Log.w(TAG, "Legacy Google Drive upload requested (no-op stub)")
        Result.failure(UnsupportedOperationException("Google Drive direct uploads are disabled"))
    }
    
    /**
     * Download a file from Google Drive (Obsolete stub)
     */
    suspend fun downloadFile(fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        Log.w(TAG, "Legacy Google Drive download requested (no-op stub)")
        Result.failure(UnsupportedOperationException("Google Drive direct downloads are disabled"))
    }
    
    /**
     * Get the file ID from a Drive URL
     */
    fun getFileIdFromUrl(driveUrl: String): String? {
        val regex = "/file/d/([a-zA-Z0-9_-]+)".toRegex()
        val matchResult = regex.find(driveUrl)
        return matchResult?.groupValues?.get(1)
    }
    
    /**
     * Delete a file from Google Drive (Obsolete stub)
     */
    suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.w(TAG, "Legacy Google Drive delete requested (no-op stub)")
        Result.failure(UnsupportedOperationException("Google Drive direct deletions are disabled"))
    }

    /**
     * Upload a file to Google Drive with callback interface (Obsolete stub)
     */
    fun uploadFileToDrive(
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        fileType: FileTypeEnum,
        callback: (Result<String>) -> Unit
    ) {
        
        // Launch a coroutine to perform the upload
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure folders are initialized
                if (IMAGES_FOLDER_ID == null || DOCUMENTS_FOLDER_ID == null) {
                    initFolders()
                }
                
                val result = uploadFile(fileUri, fileName, mimeType, fileType)
                
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val webViewLink = result.getOrNull() ?: ""
                        callback(Result.success(webViewLink))
                    } else {
                        Log.e(TAG, "Failed to upload file: ${result.exceptionOrNull()?.message}")
                        callback(Result.failure(result.exceptionOrNull() ?: Exception("Upload failed")))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadFileToDrive", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Converts a standard Google Drive webViewLink to a direct download or view link
     * that works better with image loading libraries like Glide
     *
     * @param driveUrl The original Google Drive URL
     * @param isImage Whether the file is an image
     * @return A modified URL that can be used for direct viewing/downloading
     */
    fun getDirectMediaUrl(driveUrl: String, isImage: Boolean = true): String {
        return if (driveUrl.contains("drive.google.com")) {
            if (driveUrl.contains("export=media") || driveUrl.contains("export=view") || driveUrl.contains("export=download")) {
                // URL is already in correct format
                driveUrl
            } else if (driveUrl.contains("/view")) {
                if (isImage) {
                    // For images, convert to export format=jpg
                    driveUrl.replace("/view", "/export?format=jpg")
                } else {
                    // For documents, use export=download
                    driveUrl.replace("/view", "/export?format=pdf")
                }
            } else {
                // Add export parameter
                "$driveUrl&export=download"
            }
        } else {
            // Not a Google Drive URL, return as is
            driveUrl
        }
    }

    /**
     * Converts a standard Google Drive sharing URL into a direct, viewable image link.
     * This is crucial for image loaders like Glide and Coil.
     */
    fun processGoogleDriveUrl(url: String): String {
        return try {
            val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            if (decodedUrl.contains("drive.google.com/file/d/")) {
                val fileId = decodedUrl.substringAfter("/d/").substringBefore("/")
                "https://lh3.googleusercontent.com/d/$fileId"
            } else {
                decodedUrl
            }
        } catch (e: Exception) {
            Log.e("DriveServiceHelper", "Error processing Google Drive URL: $url", e)
            url // Return original URL on error
        }
    }
} 