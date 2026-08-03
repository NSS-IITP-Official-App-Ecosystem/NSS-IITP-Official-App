package com.phad.chatapp.activities

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.phad.chatapp.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_IMAGE_URL = "image_url"
        private const val TAG = "ImageViewActivity"
        
        // Google Drive URL patterns
        private const val DRIVE_URL_PATTERN = "https://drive.google.com/file/d/"
        private const val DRIVE_PREVIEW_PATTERN = "https://drive.google.com/uc?id="
    }
    
    private lateinit var imageView: PhotoView
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: ImageView
    private lateinit var downloadButton: FloatingActionButton
    private lateinit var downloadProgressBar: ProgressBar
    private var loadAttempts = 0
    private var imageUrl: String = ""
    private var loadedImage: Bitmap? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)
        
        // Initialize views
        imageView = findViewById(R.id.full_image_view)
        progressBar = findViewById(R.id.loading_progress)
        backButton = findViewById(R.id.back_button)
        downloadButton = findViewById(R.id.download_button)
        downloadProgressBar = findViewById(R.id.download_progress)
        
        // Ensure the back button is properly configured
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }
        
        // Set up download button
        downloadButton.setOnClickListener {
            downloadImage()
        }
        
        // Get image URL from intent
        imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
        
        if (imageUrl.isEmpty()) {
            Toast.makeText(this, "No image URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Process and load the image
        val processedUrl = processGoogleDriveUrl(imageUrl)
        Log.d(TAG, "Processed URL: $processedUrl")
        loadImage(processedUrl)
    }
    
    /**
     * Process Google Drive URLs to direct download format
     */
    private fun processGoogleDriveUrl(url: String): String {
        Log.d(TAG, "Original URL: $url")
        
        try {
            // URL decode in case it's encoded
            val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            
            // Process Google Drive links
            when {
                // Format: https://drive.google.com/file/d/FILE_ID/view?usp=drivesdk
                decodedUrl.contains(DRIVE_URL_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("$DRIVE_URL_PATTERN").substringBefore("/")
                    
                    // Try different URL formats for Google Drive - some work better than others
                    // Order: googleapis content, direct download, alternative photosapi
                    val contentUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=${com.phad.chatapp.BuildConfig.GOOGLE_DRIVE_API_KEY}"
                    Log.d(TAG, "Trying Google API content URL: $contentUrl")
                    return contentUrl
                }
                // Format: https://drive.google.com/uc?id=FILE_ID
                decodedUrl.contains(DRIVE_PREVIEW_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("id=").substringBefore("&")
                    
                    // Try a different approach - direct download with export=download
                    val directUrl = "https://lh3.googleusercontent.com/d/$fileId"
                    Log.d(TAG, "Using Google Photos direct URL: $directUrl")
                    return directUrl
                }
                else -> {
                    // Return as is if not a recognized Google Drive URL
                    return decodedUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing URL: $e")
            return url
        }
    }
    
    /**
     * Load image with Glide and store the bitmap for later download
     */
    private fun loadImage(imageUrl: String) {
        Log.d(TAG, "Loading image from URL: $imageUrl")
        progressBar.visibility = View.VISIBLE
        
        try {
            // Configure Glide with caching
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .timeout(60000) // 60 second timeout
            
            // Load with Glide and save bitmap for later download
            Glide.with(applicationContext)
                .asBitmap()
                .load(imageUrl)
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        // Save bitmap for download
                        loadedImage = resource
                        
                        // Set image to view
                        imageView.setImageBitmap(resource)
                        progressBar.visibility = View.GONE
                    }
                })
            
            // Also load it directly into the view
            Glide.with(applicationContext)
                .load(imageUrl)
                .apply(options)
                .into(imageView)
            
            // Set a timeout to hide progress and try alternative URL if needed
            imageView.postDelayed({
                if (progressBar.visibility == View.VISIBLE) {
                    progressBar.visibility = View.GONE
                    
                    // Try alternative approach if first attempt seems to be taking too long
                    if (loadAttempts < 1 && imageUrl.contains("googleapis.com")) {
                        loadAttempts++
                        
                        // Extract fileId from the URL
                        val fileId = when {
                            imageUrl.contains("files/") -> imageUrl.substringAfter("files/").substringBefore("?")
                            imageUrl.contains("id=") -> imageUrl.substringAfter("id=").substringBefore("&")
                            imageUrl.contains("/d/") -> imageUrl.substringAfter("/d/").substringBefore("/")
                            else -> null
                        }
                        
                        if (fileId != null) {
                            // Try Google Photos direct URL as fallback
                            val alternativeUrl = "https://lh3.googleusercontent.com/d/$fileId"
                            Log.d(TAG, "Trying alternative URL: $alternativeUrl")
                            
                            Glide.with(applicationContext)
                                .asBitmap()
                                .load(alternativeUrl)
                                .apply(options)
                                .into(object : SimpleTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        // Save bitmap for download
                                        loadedImage = resource
                                        
                                        // Set image to view
                                        imageView.setImageBitmap(resource)
                                        progressBar.visibility = View.GONE
                                    }
                                })
                        }
                    }
                }
            }, 10000) // 10 second timeout
            
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Error loading image: ${e.message}", e)
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Download the currently displayed image
     */
    private fun downloadImage() {
        // Check if we have an image to download
        if (loadedImage == null) {
            Toast.makeText(this, "Image not yet loaded", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show download progress
        downloadButton.visibility = View.GONE
        downloadProgressBar.visibility = View.VISIBLE
        
        // Launch in coroutine to avoid blocking UI thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadedImage
                if (bitmap != null) {
                    // Generate a filename with timestamp
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "IMG_$timestamp.jpg"
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use MediaStore for Android 10+
                        saveImageWithMediaStore(bitmap, fileName)
                    } else {
                        // Use direct file access for older Android versions
                        saveImageWithDirectFile(bitmap, fileName)
                    }
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        downloadProgressBar.visibility = View.GONE
                        downloadButton.visibility = View.VISIBLE
                        Toast.makeText(this@ImageViewActivity, "Image saved to gallery", Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw IOException("Image not available")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image: ${e.message}", e)
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    downloadProgressBar.visibility = View.GONE
                    downloadButton.visibility = View.VISIBLE
                    Toast.makeText(this@ImageViewActivity, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private suspend fun saveImageWithMediaStore(bitmap: Bitmap, fileName: String) {
        // Create content values for new image
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        
        // Insert into MediaStore
        val resolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        if (imageUri != null) {
            // Open output stream and save bitmap
            resolver.openOutputStream(imageUri).use { os ->
                if (os != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
            }
            
            // Mark as not pending for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
        } else {
            throw IOException("Failed to create new MediaStore record")
        }
    }
    
    private suspend fun saveImageWithDirectFile(bitmap: Bitmap, fileName: String) {
        // Get pictures directory
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        
        // Create output file
        val imageFile = File(picturesDir, fileName)
        
        // Save bitmap to file
        var outputStream: OutputStream? = null
        try {
            outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        } finally {
            outputStream?.close()
        }
        
        // Tell gallery to scan the file
        val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val contentUri = android.net.Uri.fromFile(imageFile)
        mediaScanIntent.data = contentUri
        sendBroadcast(mediaScanIntent)
    }
    
    // Ensure back button functionality
    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed called")
        super.onBackPressed()
    }
} 