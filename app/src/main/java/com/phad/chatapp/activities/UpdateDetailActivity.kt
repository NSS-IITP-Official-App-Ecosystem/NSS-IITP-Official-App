package com.phad.chatapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.R
import com.phad.chatapp.databinding.ActivityUpdateDetailBinding
import com.phad.chatapp.models.Update
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdateDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUpdateDetailBinding
    private val TAG = "UpdateDetailActivity"
    
    companion object {
        const val EXTRA_UPDATE = "extra_update"
        const val UPDATE_ID = "UPDATE_ID"
        private const val DRIVE_URL_PATTERN = "https://drive.google.com/file/d/"
        private const val DRIVE_PREVIEW_PATTERN = "https://drive.google.com/uc?id="
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar and back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Get update from intent
        val update = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_UPDATE, Update::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_UPDATE)
        }
        
        // Check if we have an update ID instead (this happens when coming from notifications)
        val updateId = intent.getStringExtra(UPDATE_ID)
        
        if (update != null) {
            // We have a complete update object, display it
            displayUpdate(update)
        } else if (updateId != null) {
            // We only have the update ID, fetch the update from Firestore
            binding.progressBar.visibility = View.VISIBLE
            FirebaseFirestore.getInstance().collection("updates")
                .document(updateId)
                .get()
                .addOnSuccessListener { document ->
                    binding.progressBar.visibility = View.GONE
                    if (document != null && document.exists()) {
                        val fetchedUpdate = document.toObject(Update::class.java)
                        if (fetchedUpdate != null) {
                            // Ensure the ID is set
                            val updatedUpdate = if (fetchedUpdate.id.isEmpty()) {
                                fetchedUpdate.copy(id = document.id)
                            } else {
                                fetchedUpdate
                            }
                            displayUpdate(updatedUpdate)
                        } else {
                            showError("Could not parse update data")
                        }
                    } else {
                        showError("Update not found")
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    showError("Error loading update: ${e.message}")
                }
        } else {
            // No update data available
            showError("No update data provided")
        }
    }
    
    private fun displayUpdate(update: Update) {
        binding.apply {
            // Author info
            authorNameTextView.text = update.authorName
            
            // Format timestamp
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            timestampTextView.text = sdf.format(Date(update.timestamp))
            
            // Author image
            if (!update.authorImageUrl.isNullOrEmpty()) {
                Glide.with(this@UpdateDetailActivity)
                    .load(update.authorImageUrl)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.default_profile_image)
                        .error(R.drawable.default_profile_image))
                    .into(authorImageView)
            }
            
            // Title
            if (!update.title.isNullOrEmpty()) {
                titleTextView.visibility = View.VISIBLE
                titleTextView.text = update.title
            } else {
                titleTextView.visibility = View.GONE
            }
            
            // Content
            contentTextView.text = update.content
            
            // Media image - check mediaUrl only
            val mediaUrl = update.mediaUrl
            if (!mediaUrl.isNullOrEmpty()) {
                imageView.visibility = View.VISIBLE
                
                // Process Google Drive URL for display
                val displayUrl = processGoogleDriveUrl(mediaUrl)
                
                Glide.with(this@UpdateDetailActivity)
                    .load(displayUrl)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.img_placeholder)
                        .error(R.drawable.img_error))
                    .into(imageView)
                
                // Set click listener
                imageView.setOnClickListener {
                    val intent = Intent(this@UpdateDetailActivity, ImageViewActivity::class.java).apply {
                        putExtra(ImageViewActivity.EXTRA_IMAGE_URL, mediaUrl)
                    }
                    startActivity(intent)
                }
            } else {
                imageView.visibility = View.GONE
            }
            
            // Document
            if (!update.documentUrl.isNullOrEmpty()) {
                documentCard.visibility = View.VISIBLE
                documentNameText.text = update.documentName ?: "View Document"
                
                documentCard.setOnClickListener {
                    try {
                        // Use the document URL directly - it should be in the format:
                        // https://drive.google.com/file/d/FILE_ID/view?usp=sharing
                        val docUrl = update.documentUrl
                        
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        Toast.makeText(this@UpdateDetailActivity, "Opening document...", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@UpdateDetailActivity, "Cannot open document: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                documentCard.visibility = View.GONE
            }
            
            // External link - check both externalLink and url fields
            if (!update.externalLink.isNullOrEmpty()) {
                externalLinkCard.visibility = View.VISIBLE
                externalLinkText.text = update.externalLink
                
                externalLinkCard.setOnClickListener {
                    try {
                        var url = update.externalLink
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@UpdateDetailActivity, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                externalLinkCard.visibility = View.GONE
            }
            
            // Show roll number (authorId)
            if (update.authorId.isNotEmpty()) {
                rollNumberTextView.visibility = View.VISIBLE
                rollNumberTextView.text = "Roll Number: ${update.authorId}"
            } else {
                rollNumberTextView.visibility = View.GONE
            }
        }
    }
    
    private fun processGoogleDriveUrl(url: String): String {
        if (url.isBlank()) {
            return url
        }
        
        try {
            // URL decode in case it's encoded
            val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            
            // Process Google Drive links
            when {
                decodedUrl.contains(DRIVE_URL_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("$DRIVE_URL_PATTERN").substringBefore("/")
                    return "https://drive.google.com/uc?export=view&id=$fileId"
                }
                decodedUrl.contains(DRIVE_PREVIEW_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("id=").substringBefore("&")
                    return "https://drive.google.com/uc?export=view&id=$fileId" 
                }
                else -> return decodedUrl
            }
        } catch (e: Exception) {
            return url
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
        binding.contentScrollView.visibility = View.GONE
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 