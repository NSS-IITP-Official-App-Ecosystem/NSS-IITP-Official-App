package com.phad.chatapp.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Utility class to handle file storage operations with Google Drive
 */
object FileStorageUtils {
    private const val TAG = "FileStorageUtils"
    
    /**
     * Get the base media directory for the app like WhatsApp (Android/media/com.phad.chatapp/NSS App/)
     */
    private fun getMediaDir(context: Context, folderName: String): File {
        val baseMediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "NSS App").apply { mkdirs() }
        } ?: context.getExternalFilesDir(null)
        
        val specificDir = File(baseMediaDir, "Media/$folderName")
        if (!specificDir.exists()) {
            specificDir.mkdirs()
        }
        return specificDir
    }
    
    /**
     * Upload an image to Google Drive
     *
     * @param context The context
     * @param imageUri The URI of the image file
     * @param customFileName Optional custom file name
     * @return The URL of the uploaded image
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        customFileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cloudinaryHelper = CloudinaryHelper.getInstance(context)
            
            // Upload the file using Cloudinary
            val result = cloudinaryHelper.uploadImage(imageUri, "chat_media/images")
            return@withContext result.url
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
    
    /**
     * Upload a document to Google Drive
     *
     * @param context The context
     * @param documentUri The URI of the document
     * @param customFileName Optional custom file name
     * @return The URL of the uploaded document
     */
    suspend fun uploadDocument(
        context: Context,
        documentUri: Uri,
        customFileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cloudinaryHelper = CloudinaryHelper.getInstance(context)
            
            // Upload the document using Cloudinary
            val result = cloudinaryHelper.uploadDocument(documentUri, "chat_media/documents")
            return@withContext result.url
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading document", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error uploading document: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
    
    /**
     * Download a file from Google Drive
     *
     * @param context The context
     * @param fileUrl The URL of the file
     * @param fileType The type of file (determines local storage location)
     * @return The downloaded file or null on failure
     */
    suspend fun downloadFile(
        context: Context,
        fileUrl: String,
        fileType: FileTypeEnum
    ): File? = withContext(Dispatchers.IO) {
        try {
            val driveHelper = DriveServiceHelper.getInstance(context)
            
            // Extract the file ID from the URL
            val fileId = driveHelper.getFileIdFromUrl(fileUrl)
                ?: return@withContext null
            
            // Create the appropriate directory based on file type
            val directory = when (fileType) {
                FileTypeEnum.IMAGE -> File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ChatApp")
                FileTypeEnum.DOCUMENT -> File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ChatApp")
            }
            
            // Ensure the directory exists
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            // Download the file
            val result = driveHelper.downloadFile(fileId)
            
            if (result.isSuccess) {
                // Create a file to save the content
                val fileName = "download_${System.currentTimeMillis()}"
                val extension = when (fileType) {
                    FileTypeEnum.IMAGE -> ".jpg"
                    FileTypeEnum.DOCUMENT -> ".pdf"
                }
                
                val file = File(directory, "$fileName$extension")
                
                // Write the content to the file
                FileOutputStream(file).use { it.write(result.getOrNull()) }
                
                return@withContext file
            } else {
                Log.e(TAG, "Failed to download file: ${result.exceptionOrNull()?.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download file", Toast.LENGTH_SHORT).show()
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error downloading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
    
    /**
     * Get the file extension from a URI
     */
    private fun getFileExtension(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val mimeTypeMap = MimeTypeMap.getSingleton()
        
        // Try to get the extension from the content type
        val mimeType = contentResolver.getType(uri)
        
        if (mimeType != null) {
            return mimeTypeMap.getExtensionFromMimeType(mimeType) ?: ""
        }
        
        // If content type doesn't work, try from URI path
        val path = uri.path ?: return ""
        val dotPosition = path.lastIndexOf('.')
        
        return if (dotPosition >= 0 && dotPosition < path.length - 1) {
            path.substring(dotPosition + 1)
        } else {
            // Default extensions based on common file types
            when {
                mimeType?.toString()?.startsWith("image/") == true -> "jpg"
                mimeType?.toString()?.startsWith("application/pdf") == true -> "pdf"
                else -> ""
            }
        }
    }
    
    /**
     * Get the MIME type from a URI
     */
    private fun getMimeType(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        return contentResolver.getType(uri) ?: when (getFileExtension(context, uri).lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            else -> null
        }
    }
    
    /**
     * Create a temporary image file for camera captures
     */
    fun createImageFile(context: Context): File {
        // Create a unique filename with timestamp
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        
        // Get the directory for storing images (Android/media/...)
        val imageDir = getMediaDir(context, "NSS App Images")
        
        // Create the temporary file in the images directory
        return File.createTempFile(
            imageFileName,
            ".jpg",
            imageDir
        )
    }
    
    /**
     * Download a document from a URL and return a content URI that can be used to open it
     */
    suspend fun downloadDocument(context: Context, url: String): Uri? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading document from: $url")
            
            // Create directory for documents if it doesn't exist (Android/media/...)
            val documentsDir = getMediaDir(context, "NSS App Documents")
            
            val fileExtension = getFileExtensionFromUrl(url)
            
            // Process Google Drive URL if needed - extract the file ID
            var downloadUrl = url
            var uniqueId = hashString(url) // Default to URL hash
            
            if (url.contains("drive.google.com/file/d/")) {
                // Extract the file ID from the standard drive URL format
                val fileId = url.substringAfter("/file/d/").substringBefore("/view")
                Log.d(TAG, "Extracted Drive file ID: $fileId")
                
                uniqueId = fileId
                
                // Use direct download URL format
                downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                Log.d(TAG, "Using download URL: $downloadUrl")
            } else if (url.contains("drive.google.com/uc?id=") || url.contains("drive.google.com/open?id=")) {
                val fileId = url.substringAfter("id=").substringBefore("&")
                uniqueId = fileId
                downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            }
            
            // Create a deterministic filename based on the unique ID
            val fileName = "DOC_${uniqueId}.$fileExtension"
            val outputFile = File(documentsDir, fileName)
            
            // CACHE CHECK: If file already exists and is not empty, return it immediately
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Cache hit: Document already downloaded at ${outputFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Opening from cache...", Toast.LENGTH_SHORT).show()
                }
                return@withContext FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
            }
            
            // Open connection to URL
            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30000 // 30 seconds timeout
            connection.readTimeout = 60000    // 60 seconds read timeout
            connection.connect()
            
            // Check if connection is successful
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP $responseCode: ${connection.responseMessage}")
                
                // If we get a redirect for Google Drive, follow it manually
                if ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                     responseCode == HttpURLConnection.HTTP_MOVED_PERM) &&
                    url.contains("drive.google.com")) {
                    
                    val newUrl = connection.getHeaderField("Location")
                    Log.d(TAG, "Following redirect to: $newUrl")
                    
                    // Close current connection
                    connection.disconnect()
                    
                    // Try the new URL
                    return@withContext downloadDocument(context, newUrl)
                }
                
                return@withContext null
            }
            
            // Log content type and length
            val contentType = connection.contentType
            val contentLength = connection.contentLength
            Log.d(TAG, "Document content type: $contentType, size: $contentLength bytes")
            
            // Download the file
            val input = connection.inputStream
            val output = FileOutputStream(outputFile)
            
            val buffer = ByteArray(8192) // Larger buffer for efficiency
            var bytesRead: Int
            var totalBytesRead = 0
            val contentLengthLong = connection.contentLength.toLong()
            
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // Log progress for large files
                if (contentLengthLong > 0 && totalBytesRead % (contentLengthLong / 10).coerceAtLeast(1) < 8192) {
                    val progress = (totalBytesRead * 100 / contentLengthLong)
                    Log.d(TAG, "Download progress: $progress%")
                }
            }
            
            output.flush()
            output.close()
            input.close()
            connection.disconnect()
            
            Log.d(TAG, "Document downloaded successfully: ${outputFile.length()} bytes")
            
            // Verify the file was downloaded properly and has content
            if (outputFile.length() == 0L) {
                Log.e(TAG, "Downloaded file is empty")
                return@withContext null
            }
            
            // Get content URI using FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
            
            Log.d(TAG, "Document downloaded to: ${outputFile.absolutePath}")
            Log.d(TAG, "Content URI: $contentUri")
            
            return@withContext contentUri
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading document: ${e.message}", e)
            e.printStackTrace() // Print stack trace for debugging
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to download document: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return@withContext null
        }
    }
    
    /**
     * Get file extension from a URL
     */
    private fun getFileExtensionFromUrl(url: String): String {
        try {
            // Remove query parameters
            val urlWithoutParams = url.substringBefore('?')
            
            // Get the filename from the URL path
            val fileName = urlWithoutParams.substringAfterLast('/', "")
            Log.d(TAG, "Detected filename from URL: $fileName")
            
            // Extract extension from filename
            val dotPosition = fileName.lastIndexOf('.')
            
            if (dotPosition >= 0 && dotPosition < fileName.length - 1) {
                val extension = fileName.substring(dotPosition + 1).lowercase()
                
                // Validate that this is a known document extension
                return when (extension) {
                    "pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx" -> extension
                    else -> {
                        // For Google Drive URLs, most likely PDFs
                        if (url.contains("drive.google.com")) {
                            "pdf" // Default to PDF for Google Drive
                        } else {
                            extension
                        }
                    }
                }
            }
            
            // For Google Drive URLs without extension
            if (url.contains("drive.google.com")) {
                return "pdf" // Default to PDF for Google Drive
            }
            
            // Try to guess from content type if available in URL
            if (url.contains("=pdf") || url.contains("/pdf")) {
                return "pdf"
            }
            
            // Default to PDF if extension not found
            return "pdf"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file extension from URL: $e")
            return "pdf" // Default to PDF on error
        }
    }
    
    /**
     * Generate MD5 hash of a string
     */
    private fun hashString(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
} 