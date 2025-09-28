package com.phad.chatapp.fragments

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.phad.chatapp.databinding.FragmentFullScreenImageBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class FullScreenImageFragment : Fragment() {

    private var _binding: FragmentFullScreenImageBinding? = null
    private val binding get() = _binding!!
    private val args: FullScreenImageFragmentArgs by navArgs()
    private var loadedImage: Bitmap? = null // To store the loaded image for download

    // Request launcher for storage permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, attempt download again
                downloadImage()
            } else {
                // Permission denied
                Toast.makeText(requireContext(), "Storage permission denied. Cannot download image.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFullScreenImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets to avoid overlap with status bar
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.buttonClose.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarsInsets.top + (16 * resources.displayMetrics.density).toInt() // Add original 16dp margin
            }
            insets // Consume the insets
        }

        val imageUrl = args.imageUrl

        // Load image with Glide and store bitmap for download
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    loadedImage = resource // Store bitmap
                    binding.fullScreenImageView.setImageBitmap(resource) // Display image
                }
            })

        // Set up click listener for the close button
        binding.buttonClose.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up click listener for the download button
        binding.buttonDownload.setOnClickListener {
            checkAndRequestPermissionAndDownload()
        }
    }

    /**
     * Check for storage permission and request if needed, then initiate download
     */
    private fun checkAndRequestPermissionAndDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // For older Android versions, check WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                // Permission already granted, proceed with download
                downloadImage()
            }
        } else {
            // For Android 10 (Q) and above, no explicit storage permission is needed for MediaStore
            downloadImage()
        }
    }

    /**
     * Download and save the currently displayed image
     */
    private fun downloadImage() {
        // Check if we have an image to download
        val bitmap = loadedImage
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Image not yet loaded", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Downloading image...", Toast.LENGTH_SHORT).show()

        // Launch in coroutine to avoid blocking UI thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IMG_$timestamp.jpg"

                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore for Android 10+
                    saveImageWithMediaStore(bitmap, fileName)
                } else {
                    // Use direct file access for older Android versions
                    saveImageToExternalStorage(bitmap, fileName)
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), "Image saved successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FullScreenImage", "Error saving image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Save image using MediaStore for Android 10 (Q) and above
     */
    private fun saveImageWithMediaStore(bitmap: Bitmap, fileName: String): Boolean {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = requireContext().contentResolver.insert(collection, contentValues)
        uri ?: return false

        try {
            requireContext().contentResolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    return true
                }
            }
        } catch (e: IOException) {
            Log.e("FullScreenImage", "Error saving image with MediaStore: ${e.message}", e)
            // Clean up incomplete entry
            requireContext().contentResolver.delete(uri, null, null)
            return false
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                requireContext().contentResolver.update(uri, contentValues, null, null)
            }
        }
        return false // Should be covered by try/catch
    }

    /**
     * Save image to external storage (Pictures directory) for older Android versions
     */
    private fun saveImageToExternalStorage(bitmap: Bitmap, fileName: String): Boolean {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageFile = File(imagesDir, fileName)

        try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                // Make sure the file is visible in galleries
                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(imageFile)
                requireContext().sendBroadcast(mediaScanIntent)
                return true
            }
        } catch (e: IOException) {
            Log.e("FullScreenImage", "Error saving image to external storage: ${e.message}", e)
            return false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        loadedImage = null // Release the bitmap
    }
} 