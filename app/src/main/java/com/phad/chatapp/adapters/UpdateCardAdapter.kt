package com.phad.chatapp.adapters

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.phad.chatapp.R
import com.phad.chatapp.activities.ImageViewActivity
import com.phad.chatapp.databinding.ItemUpdateCardBinding
import com.phad.chatapp.models.Update
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateCardAdapter : ListAdapter<Update, UpdateCardAdapter.UpdateCardViewHolder>(UpdateDiffCallback()) {
    private val TAG = "UpdateCardAdapter"
    
    // Interface for click handlers
    interface OnUpdateClickListener {
        fun onUpdateClick(update: Update)
    }
    
    private var updateClickListener: OnUpdateClickListener? = null
    
    fun setOnUpdateClickListener(listener: OnUpdateClickListener) {
        this.updateClickListener = listener
    }
    
    // Constants for Google Drive URLs
    companion object {
        private const val DRIVE_URL_PATTERN = "https://drive.google.com/file/d/"
        private const val DRIVE_PREVIEW_PATTERN = "https://drive.google.com/uc?id="
        private const val DRIVE_EXPORT_PATTERN = "https://drive.google.com/uc?export=view&id="
    }
    
    // Global Glide request options for consistent image loading
    private val glideRequestOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .timeout(60000) // 60 second timeout
        .placeholder(R.drawable.img_placeholder)
        .error(R.drawable.img_error)
    
    /**
     * Updates the adapter's data with a new list of updates
     * @param newUpdates The new list of updates to display
     */
    fun updateList(newUpdates: List<Update>) {
        submitList(newUpdates)
    }
    
    /**
     * Process Google Drive URLs to make them viewable
     */
    private fun processGoogleDriveUrl(url: String): String {
        Log.d(TAG, "Processing URL: $url")
        
        if (url.isBlank()) {
            return url
        }
        
        try {
            // URL decode in case it's encoded
            val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            
            // Process Google Drive links
            when {
                // Format: https://drive.google.com/file/d/FILE_ID/view?usp=drivesdk
                decodedUrl.contains(DRIVE_URL_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("$DRIVE_URL_PATTERN").substringBefore("/")
                    
                    // Use the export=view pattern for images which works better
                    val contentUrl = "https://drive.google.com/uc?export=view&id=$fileId"
                    Log.d(TAG, "Converted to export URL: $contentUrl")
                    return contentUrl
                }
                // Format: https://drive.google.com/uc?id=FILE_ID
                decodedUrl.contains(DRIVE_PREVIEW_PATTERN) -> {
                    val fileId = decodedUrl.substringAfter("id=").substringBefore("&")
                    
                    // Use the export=view pattern
                    val exportUrl = "https://drive.google.com/uc?export=view&id=$fileId" 
                    Log.d(TAG, "Converted to export URL: $exportUrl")
                    return exportUrl
                }
                else -> {
                    // Return as is if not a recognized Google Drive URL
                    return decodedUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing URL: ${e.message}")
            return url
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateCardViewHolder {
        val binding = ItemUpdateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UpdateCardViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: UpdateCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class UpdateCardViewHolder(private val binding: ItemUpdateCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(update: Update) {
            binding.apply {
                // Make the whole card clickable to show complete message
                root.setOnClickListener {
                    updateClickListener?.onUpdateClick(update)
                }
                
                // Set update type badge
                categoryBadge.text = update.getUpdateTypeDisplay().uppercase()
                setCategoryBadgeColor(update.updateType)
                
                authorNameTextView.text = update.authorName
                timestampTextView.text = formatDate(update.timestamp)
                
                // Set title visibility based on content
                if (update.title.isNullOrEmpty()) {
                    titleTextView.visibility = View.GONE
                } else {
                    titleTextView.visibility = View.VISIBLE
                    titleTextView.text = update.title
                }
                
                // Set content
                contentTextView.text = update.content
                
                // Event date and location features are not available in the simplified Update model
                // eventDateContainer.visibility = View.GONE
                // locationContainer.visibility = View.GONE
                
                // Handle profile image
                if (!update.authorImageUrl.isNullOrEmpty()) {
                    // Load author profile image
                    Glide.with(authorImageView.context)
                        .load(update.authorImageUrl)
                        .apply(glideRequestOptions)
                        .into(authorImageView)
                } else {
                    // Use default image
                    authorImageView.setImageResource(R.drawable.default_profile_image)
                }
                
                // Media handling
                val mediaUrl = update.mediaUrl
                val instagramUrl = update.instagramUrl
                
                
                if (!instagramUrl.isNullOrEmpty()) {
                    imageView.visibility = View.VISIBLE
                    // Instagram doesn't provide direct thumbnail URLs, use a placeholder
                    Glide.with(imageView.context)
                        .load(android.R.color.darker_gray)
                        .apply(glideRequestOptions)
                        .into(imageView)
                        
                    imageView.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(instagramUrl))
                            imageView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(imageView.context, "Cannot open Instagram: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (!mediaUrl.isNullOrEmpty()) {
                    imageView.visibility = View.VISIBLE
                    
                    if (update.isVideo) {
                        // Handle Direct Video
                        Glide.with(imageView.context)
                            .load(mediaUrl)
                            .apply(glideRequestOptions)
                            .into(imageView)
                            
                        imageView.setOnClickListener {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl))
                                intent.setDataAndType(Uri.parse(mediaUrl), "video/*")
                                imageView.context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(imageView.context, "Cannot play video: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // Handle Image - Existing logic
                        Log.d(TAG, "Original media URL: $mediaUrl")
                        
                        // Process the URL to make it viewable
                        val displayUrl = processGoogleDriveUrl(mediaUrl)
                        
                        // Reset any previous image to prevent ghosting
                        imageView.setImageDrawable(null)
                        
                        // Load and display the image with enhanced Glide configuration
                        Glide.with(imageView.context)
                            .load(displayUrl)
                            .apply(glideRequestOptions)
                            .transition(DrawableTransitionOptions.withCrossFade()) 
                            .override(800, 600) 
                            .centerCrop() 
                            .into(imageView)
                        
                        // Set click listener to open in ImageViewActivity
                        imageView.setOnClickListener {
                            try {
                                val intent = Intent(imageView.context, ImageViewActivity::class.java).apply {
                                    putExtra(ImageViewActivity.EXTRA_IMAGE_URL, mediaUrl)
                                }
                                imageView.context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error opening image: ${e.message}")
                            }
                        }
                    }
                } else {
                    imageView.visibility = View.GONE
                }
                
                // Handle external link - check both externalLink and url fields
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
                            externalLinkCard.context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening link: ${e.message}")
                            Toast.makeText(externalLinkCard.context, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    externalLinkCard.visibility = View.GONE
                }
                
                // Handle document
                if (!update.documentUrl.isNullOrEmpty()) {
                    documentCard.visibility = View.VISIBLE
                    documentNameText.text = update.documentName ?: "View Document"
                    
                    documentCard.setOnClickListener {
                        try {
                            // Use the document URL directly - it should be in the format:
                            // https://drive.google.com/file/d/FILE_ID/view?usp=sharing
                            val docUrl = update.documentUrl
                            
                            // Show toast to indicate download is starting
                            Toast.makeText(documentCard.context, "Downloading document...", Toast.LENGTH_SHORT).show()
                            
                            // Download and open the document in a coroutine - don't process the URL for PDFs
                            // as the original format works better for downloads
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    // Download the document using the original URL
                                    val localUri = withContext(Dispatchers.IO) {
                                        com.phad.chatapp.utils.FileStorageUtils.downloadDocument(documentCard.context, docUrl)
                                    }
                                    
                                    if (localUri != null) {
                                        // Create intent to open with appropriate viewer
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(localUri, "application/pdf")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        
                                        try {
                                            documentCard.context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error opening document with specific mime type: ${e.message}", e)
                                            
                                            // Try again with generic mime type
                                            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(localUri, "*/*")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            documentCard.context.startActivity(genericIntent)
                                        }
                                    } else {
                                        // If download fails, try to open in browser as fallback
                                        Toast.makeText(documentCard.context, "Opening in browser instead...", Toast.LENGTH_SHORT).show()
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        documentCard.context.startActivity(browserIntent)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error downloading document: ${e.message}", e)
                                    Toast.makeText(documentCard.context, "Cannot download document: ${e.message}", Toast.LENGTH_SHORT).show()
                                    
                                    // Fallback to direct opening
                                    try {
                                        val directIntent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                                        directIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        documentCard.context.startActivity(directIntent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error opening document directly: ${e.message}", e)
                                        Toast.makeText(documentCard.context, "Cannot open document: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening document: ${e.message}")
                            Toast.makeText(documentCard.context, "Cannot open document: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    documentCard.visibility = View.GONE
                }
                
                // Tags feature not available in simplified Update model
                // tagsChipGroup.visibility = View.GONE
            }
        }
        
        private fun setCategoryBadgeColor(updateType: Int) {
            val color = when (updateType) {
                1 -> android.graphics.Color.parseColor("#4CAF50") // Green for Teaching Wing
                2 -> android.graphics.Color.parseColor("#FF9800") // Orange for NSS
                3 -> android.graphics.Color.parseColor("#9C27B0") // Purple for Both
                else -> android.graphics.Color.parseColor("#2196F3") // Blue (default)
            }
            
            binding.categoryBadge.background.setTint(color)
        }
        
        private fun formatDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} minutes ago"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hours ago"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
}

/**
 * DiffCallback for the Update items to efficiently update RecyclerView
 */
class UpdateDiffCallback : DiffUtil.ItemCallback<Update>() {
    override fun areItemsTheSame(oldItem: Update, newItem: Update): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: Update, newItem: Update): Boolean {
        return oldItem == newItem
    }
} 