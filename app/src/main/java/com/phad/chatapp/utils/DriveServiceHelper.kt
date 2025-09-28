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
     * Initialize media folders
     */
    suspend fun initFolders() = withContext(Dispatchers.IO) {
        try {
            // Check if media folders exist, create them if they don't
            IMAGES_FOLDER_ID = getOrCreateFolder("images")
            DOCUMENTS_FOLDER_ID = getOrCreateFolder("documents")
            
            Log.d(TAG, "Folders initialized - Images: $IMAGES_FOLDER_ID, Documents: $DOCUMENTS_FOLDER_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing folders", e)
        }
    }
    
    /**
     * Get a folder ID by name, or create it if it doesn't exist
     */
    private suspend fun getOrCreateFolder(folderName: String): String = withContext(Dispatchers.IO) {
        // Search for the folder first
        val result = driveService.files().list()
            .setQ("name = '$folderName' and '$ROOT_FOLDER_ID' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        
        // If folder exists, return its ID
        if (result.files.isNotEmpty()) {
            return@withContext result.files[0].id
        }
        
        // If folder doesn't exist, create it
        val folderMetadata = DriveFile()
            .setName(folderName)
            .setMimeType("application/vnd.google-apps.folder")
            .setParents(listOf(ROOT_FOLDER_ID))
        
        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        
        return@withContext folder.id
    }
    
    /**
     * Upload a file to Google Drive
     * 
     * @param fileUri The URI of the file to upload
     * @param fileName The name to give the file in Drive
     * @param mimeType The MIME type of the file
     * @param fileType The type of file (IMAGE, DOCUMENT)
     * @return The share URL for the uploaded file
     */
    suspend fun uploadFile(
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        fileType: FileTypeEnum
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get the parent folder ID based on file type
            val parentFolderId = when (fileType) {
                FileTypeEnum.IMAGE -> IMAGES_FOLDER_ID
                FileTypeEnum.DOCUMENT -> DOCUMENTS_FOLDER_ID
            } ?: run {
                // Initialize folders if they're not set
                initFolders()
                when (fileType) {
                    FileTypeEnum.IMAGE -> IMAGES_FOLDER_ID
                    FileTypeEnum.DOCUMENT -> DOCUMENTS_FOLDER_ID
                }
            }
            
            if (parentFolderId == null) {
                return@withContext Result.failure(IOException("Failed to create or access parent folder"))
            }
            
            // Create a temporary file
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(IOException("Failed to open input stream"))
            
            val tempFile = java.io.File.createTempFile("upload", null)
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            
            // Prepare file metadata
            val fileMetadata = DriveFile()
                .setName(fileName)
                .setParents(listOf(parentFolderId))
            
            // Create file content from the temp file
            val fileContent = FileContent(mimeType, tempFile)
            
            // Upload the file
            val uploadedFile = driveService.files().create(fileMetadata, fileContent)
                .setFields("id, webViewLink")
                .execute()
            
            // Make the file publicly accessible
            val permission = com.google.api.services.drive.model.Permission()
                .setType("anyone")
                .setRole("reader")
            
            driveService.permissions().create(uploadedFile.id, permission)
                .setFields("id")
                .execute()
            
            // Clean up the temp file
            tempFile.delete()
            
            // Return the webViewLink that can be used to access the file
            val webViewLink = uploadedFile.webViewLink ?: 
                "https://drive.google.com/file/d/${uploadedFile.id}/view"
            
            Log.d(TAG, "File uploaded successfully: $webViewLink")
            Result.success(webViewLink)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download a file from Google Drive
     *
     * @param fileId The ID of the file to download
     * @return The file content as a byte array
     */
    suspend fun downloadFile(fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)
            
            Result.success(outputStream.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the file ID from a Drive URL
     * 
     * @param driveUrl The Google Drive URL
     * @return The file ID
     */
    fun getFileIdFromUrl(driveUrl: String): String? {
        // Pattern: https://drive.google.com/file/d/FILE_ID/view
        val regex = "/file/d/([a-zA-Z0-9_-]+)".toRegex()
        val matchResult = regex.find(driveUrl)
        return matchResult?.groupValues?.get(1)
    }
    
    /**
     * Delete a file from Google Drive
     *
     * @param fileId The ID of the file to delete
     */
    suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            driveService.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            Result.failure(e)
        }
    }

    /**
     * Upload a file to Google Drive with callback interface for use in non-coroutine contexts
     * 
     * @param fileUri The URI of the file to upload
     * @param fileName The name to give the file in Drive
     * @param mimeType The MIME type of the file
     * @param callback A callback to report success/failure and file data
     */
    fun uploadFileToDrive(
        fileUri: Uri, 
        fileName: String, 
        mimeType: String,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        // Determine file type based on MIME type
        val fileType = if (mimeType.startsWith("image/")) {
            FileTypeEnum.IMAGE
        } else {
            FileTypeEnum.DOCUMENT
        }
        
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
                        val webViewLink = result.getOrNull()
                        // Get file ID from link
                        val fileId = getFileIdFromUrl(webViewLink ?: "")
                        callback(true, fileId, webViewLink)
                    } else {
                        Log.e(TAG, "Failed to upload file: ${result.exceptionOrNull()?.message}")
                        callback(false, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadFileToDrive", e)
                withContext(Dispatchers.Main) {
                    callback(false, null, null)
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