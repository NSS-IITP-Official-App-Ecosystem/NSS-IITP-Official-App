package com.phad.chatapp.utils

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.phad.chatapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Helper class to handle file attachments for chat messages
 */
class AttachmentHandler(private val activity: AppCompatActivity) {
    private val TAG = "AttachmentHandler"
    
    // File that will be used to store camera photo
    private var photoFile: File? = null
    private var photoUri: Uri? = null
    
    // Callback for when a file is selected or captured
    private var onFileSelectedListener: ((fileUri: Uri, fileType: FileTypeEnum) -> Unit)? = null
    
    // Activity result launcher for picking images from gallery
    val pickImageLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                Log.d(TAG, "Image selected from gallery: $uri")
                onFileSelectedListener?.invoke(uri, FileTypeEnum.IMAGE)
            }
        }
    }
    
    // Activity result launcher for capturing photos with camera
    val takePhotoLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri?.let { uri ->
                Log.d(TAG, "Photo captured: $uri")
                onFileSelectedListener?.invoke(uri, FileTypeEnum.IMAGE)
            }
        }
    }
    
    // Activity result launcher for picking documents
    val pickDocumentLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                Log.d(TAG, "Document selected: $uri")
                onFileSelectedListener?.invoke(uri, FileTypeEnum.DOCUMENT)
            }
        }
    }
    
    /**
     * Set a listener for when a file is selected
     */
    fun setOnFileSelectedListener(listener: (fileUri: Uri, fileType: FileTypeEnum) -> Unit) {
        onFileSelectedListener = listener
    }
    
    /**
     * Launch gallery to pick an image
     */
    fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }
    
    /**
     * Launch camera to take a photo
     */
    private fun launchCameraIntent() {
        try {
            // Create the photo file
            photoFile = FileStorageUtils.createImageFile(activity)
            
            photoFile?.let { file ->
                // Get the URI for the file using FileProvider
                photoUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    file
                )
                
                // Create and launch the camera intent
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                takePhotoLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo", e)
            Toast.makeText(activity, "Error launching camera", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launch camera to take a photo
     */
    fun takePhoto() {
        val prefs = activity.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val hasSeenIntro = prefs.getBoolean("has_seen_camera_intro", false)

        if (hasSeenIntro) {
            launchCameraIntent()
        } else {
            // Mark as seen immediately so it never reappears
            prefs.edit().putBoolean("has_seen_camera_intro", true).apply()

            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Camera Notice")
                .setMessage("This will open your device's camera app to capture a photo.")
                .setPositiveButton("Open Camera") { _, _ ->
                    launchCameraIntent()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    /**
     * Launch document picker to select a document
     */
    fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
        ))
        pickDocumentLauncher.launch(intent)
    }
    
    /**
     * Upload a file to Google Drive
     */
    fun uploadFile(uri: Uri, fileType: FileTypeEnum, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Show upload progress toast
                Toast.makeText(activity, "Uploading file...", Toast.LENGTH_SHORT).show()
                
                // Upload the file using appropriate method based on file type
                val fileUrl = when (fileType) {
                    FileTypeEnum.IMAGE -> FileStorageUtils.uploadImage(activity, uri)
                    FileTypeEnum.DOCUMENT -> FileStorageUtils.uploadDocument(activity, uri)
                    else -> null
                }
                
                if (fileUrl != null) {
                    Toast.makeText(activity, "File uploaded successfully", Toast.LENGTH_SHORT).show()
                }
                
                callback(fileUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file", e)
                Toast.makeText(activity, "Error uploading file: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
        }
    }
    
    /**
     * Initialize the Drive service and folders
     */
    fun initDriveService() {
        // No-op: Drive folder initialization is obsolete since uploads use Cloudinary
        // and downloads use the authenticated server endpoint.
        Log.d(TAG, "Drive service initialization skipped (no-op)")
    }
} 