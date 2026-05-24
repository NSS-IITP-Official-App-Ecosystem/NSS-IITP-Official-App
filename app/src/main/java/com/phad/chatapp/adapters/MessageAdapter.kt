package com.phad.chatapp.adapters

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.R
import com.phad.chatapp.activities.ImageViewActivity
import com.phad.chatapp.models.Message
import com.phad.chatapp.utils.FileStorageUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: ((Message) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    // Define view types
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val TAG = "MessageAdapter"
    }
    
    private var messages: List<Message> = emptyList()
    private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val senderNames = ConcurrentHashMap<String, String>() // Cache for sender names
    
    // Patterns for detecting mentions
    private val EVERYONE_PATTERN = Pattern.compile("@everyone\\b", Pattern.CASE_INSENSITIVE)
    private val USER_MENTION_PATTERN = Pattern.compile("@([0-9A-Z]{8}|[0-9]{4}[A-Z]{2}[0-9]{2})\\b")
    private val IMPORTANT_PATTERN = Pattern.compile("@important\\b", Pattern.CASE_INSENSITIVE)
    
    // Colors for highlighting
    private val EVERYONE_COLOR = Color.parseColor("#FFC107") // Amber/Yellow for @everyone
    private val MENTION_COLOR = Color.parseColor("#4CAF50") // Green for @rollNumber mentions
    private val IMPORTANT_COLOR = Color.parseColor("#FF9900") // Neon Orange for @important
    
    // Add property for recyclerView reference
    private lateinit var recyclerView: RecyclerView
    
    // Request options for Glide image loading
    private val glideRequestOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original & resized images
        .placeholder(R.drawable.ic_image_placeholder) // Add a placeholder drawable
        .error(R.drawable.ic_image_error) // Add an error drawable
        .timeout(60000) // Increase timeout to 60 seconds for Google Drive images
        .fallback(R.drawable.ic_image_error) // Fallback for null URLs
    
    // Update messages and notify adapter
    fun updateMessages(newMessages: List<Message>) {
        this.messages = newMessages
        notifyDataSetChanged()
        
        // Pre-fetch sender names for all messages to reduce Firestore calls
        newMessages.forEach { message ->
            if (!senderNames.containsKey(message.sender) && message.sender != currentUserId) {
                fetchSenderName(message.sender)
            }
        }
    }
    
    override fun getItemCount(): Int = messages.size
    
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.sender == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        
        when (holder) {
            is SentMessageViewHolder -> {
                holder.bind(message)
            }
            is ReceivedMessageViewHolder -> {
                holder.bind(message)
            }
        }
    }
    
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }
    
    private fun fetchSenderName(rollNumber: String) {
        if (rollNumber.isEmpty()) return
        
        // Check cache first
        if (senderNames.containsKey(rollNumber)) return
        
        // Default to roll number until we get the name
        senderNames[rollNumber] = rollNumber
        
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(rollNumber)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    if (!name.isNullOrEmpty()) {
                        senderNames[rollNumber] = name
                        
                        // Notify adapter to update views
                        notifyDataSetChanged()
                        Log.d(TAG, "Fetched name for $rollNumber: $name")
                    }
                } else {
                    Log.d(TAG, "No user found with roll number: $rollNumber")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching sender name for $rollNumber", e)
            }
    }
    
    /**
     * Format message text with mention highlighting and important tag
     */
    private fun formatMessageText(message: Message): CharSequence {
        // Create the base text to work with
        val text = message.text
        
        // Check if this is an important message
        val hasImportantTag = IMPORTANT_PATTERN.matcher(text).find()
        
        // If it's an important message, add warning emoji and prepare special formatting
        val displayText = if (hasImportantTag) {
            // Remove the @important tag and add the warning emoji
            val cleanText = text.replace("@important", "", ignoreCase = true).trim()
            "‼️ $cleanText"
        } else {
            text
        }
        
        // Create a spannable for styling
        val spannableString = SpannableString(displayText)
        
        // For important messages, color the entire text red
        if (hasImportantTag) {
            spannableString.setSpan(
                ForegroundColorSpan(IMPORTANT_COLOR),
                0, displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Highlight @everyone mentions
        val everyoneMatcher = EVERYONE_PATTERN.matcher(displayText)
        while (everyoneMatcher.find()) {
            val start = everyoneMatcher.start()
            val end = everyoneMatcher.end()
            
            // Add background color span for @everyone
            spannableString.setSpan(
                BackgroundColorSpan(EVERYONE_COLOR),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Add text color for better contrast
            spannableString.setSpan(
                ForegroundColorSpan(Color.BLACK),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Highlight roll number mentions
        val mentionMatcher = USER_MENTION_PATTERN.matcher(displayText)
        while (mentionMatcher.find()) {
            val start = mentionMatcher.start()
            val end = mentionMatcher.end()
            val mentionedUser = mentionMatcher.group(1)
            
            // Add background color span for roll number mention
            spannableString.setSpan(
                BackgroundColorSpan(MENTION_COLOR),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Add text color for better contrast
            spannableString.setSpan(
                ForegroundColorSpan(Color.WHITE),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // If the current user is mentioned, make it more prominent
            if (mentionedUser == currentUserId) {
                spannableString.setSpan(
                    ForegroundColorSpan(Color.YELLOW),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        return spannableString
    }
    
    // ViewHolder for sent messages
    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.text_message_body)
        private val messageTime: TextView = itemView.findViewById(R.id.text_message_time)
        private val messageStatus: TextView = itemView.findViewById(R.id.text_message_status)
        private val imageContent: ImageView = itemView.findViewById(R.id.image_message_content)
        
        fun bind(message: Message) {
            // Check if this is a media message
            if (message.mediaType.isNotEmpty() || message.contentType.isNotEmpty()) {
                // Set icon and text based on media type
                val mediaType = message.mediaType.ifEmpty { message.contentType }
                when (mediaType) {
                    "image" -> {
                        // Show image, hide text
                        messageContent.visibility = View.GONE
                        imageContent.visibility = View.VISIBLE
                        
                        // Log the image URL being loaded
                        Log.d(TAG, "Loading image from URL: ${message.text}")
                        
                        // Load image with Glide - improved configuration
                        Glide.with(itemView.context.applicationContext) // Use application context to avoid leaks
                            .load(processGoogleDriveUrl(message.text)) // Process Google Drive URLs
                            .apply(glideRequestOptions)
                            .override(600, 600) // Request a larger size for better quality
                            .centerInside() // Better scaling approach
                            .into(imageContent)
                        
                        // Handle click to open in full-screen
                        imageContent.setOnClickListener {
                            // Show loading indicator before opening URL
                            Toast.makeText(itemView.context, "Opening image, please wait...", Toast.LENGTH_SHORT).show()
                            openMediaUrl(message.text, mediaType)
                        }
                    }
                    "document" -> {
                        // Hide image, show text for document
                        messageContent.visibility = View.VISIBLE
                        imageContent.visibility = View.GONE
                        messageContent.text = "📄 [Document]"
                        messageContent.setOnClickListener {
                            openMediaUrl(message.text, mediaType)
                        }
                    }
                    else -> {
                        // Default display for other types
                        messageContent.visibility = View.VISIBLE
                        imageContent.visibility = View.GONE
                        messageContent.text = formatMessageText(message)
                    }
                }
            } else {
                // Regular text message
                messageContent.visibility = View.VISIBLE
                imageContent.visibility = View.GONE
                messageContent.text = formatMessageText(message)
            }
            
            // Format and set time
            val time = dateFormat.format(message.timestamp.toDate())
            messageTime.text = time
            
            // Set read status
            val allParticipantsRead = message.read.all { (userId, isRead) -> 
                userId == currentUserId || isRead
            }
            
            val hasReadBy = message.read.entries.filter { (userId, isRead) -> 
                userId != currentUserId && isRead
            }.size
            
            val totalRecipients = message.read.size - 1 // Exclude sender
            
            if (message.groupId.isNotEmpty()) {
                // This is a group message
                if (allParticipantsRead) {
                    messageStatus.text = "Read by all"
                } else if (hasReadBy > 0) {
                    messageStatus.text = "Read by $hasReadBy of $totalRecipients"
                } else {
                    messageStatus.text = "Delivered"
                }
            } else {
                // This is a direct message
                val otherUserRead = message.read.entries.firstOrNull { it.key != currentUserId }?.value ?: false
                messageStatus.text = if (otherUserRead) "Read" else "Delivered"
            }
            
            // Add long click listener for message deletion
            itemView.setOnLongClickListener {
                onMessageLongClick?.invoke(message)
                true
            }
        }
    }
    
    // ViewHolder for received messages
    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.text_message_body)
        private val messageTime: TextView = itemView.findViewById(R.id.text_message_time)
        private val senderName: TextView = itemView.findViewById(R.id.text_message_name)
        private val imageContent: ImageView = itemView.findViewById(R.id.image_message_content)
        
        fun bind(message: Message) {
            // Check if this is a media message
            if (message.mediaType.isNotEmpty() || message.contentType.isNotEmpty()) {
                // Set icon and text based on media type
                val mediaType = message.mediaType.ifEmpty { message.contentType }
                when (mediaType) {
                    "image" -> {
                        // Show image, hide text
                        messageContent.visibility = View.GONE
                        imageContent.visibility = View.VISIBLE
                        
                        // Log the image URL being loaded
                        Log.d(TAG, "Loading image from URL: ${message.text}")
                        
                        // Load image with Glide - improved configuration
                        Glide.with(itemView.context.applicationContext) // Use application context to avoid leaks
                            .load(processGoogleDriveUrl(message.text)) // Process Google Drive URLs
                            .apply(glideRequestOptions)
                            .override(600, 600) // Request a larger size for better quality
                            .centerInside() // Better scaling approach
                            .into(imageContent)
                        
                        // Handle click to open in full-screen
                        imageContent.setOnClickListener {
                            // Show loading indicator before opening URL
                            Toast.makeText(itemView.context, "Opening image, please wait...", Toast.LENGTH_SHORT).show()
                            openMediaUrl(message.text, mediaType)
                        }
                    }
                    "document" -> {
                        // Hide image, show text for document
                        messageContent.visibility = View.VISIBLE
                        imageContent.visibility = View.GONE
                        messageContent.text = "📄 [Document]"
                        messageContent.setOnClickListener {
                            openMediaUrl(message.text, mediaType)
                        }
                    }
                    else -> {
                        // Default display for other types
                        messageContent.visibility = View.VISIBLE
                        imageContent.visibility = View.GONE
                        messageContent.text = formatMessageText(message)
                    }
                }
            } else {
                // Regular text message
                messageContent.visibility = View.VISIBLE
                imageContent.visibility = View.GONE
                messageContent.text = formatMessageText(message)
            }
            
            // Format and set time
            val time = dateFormat.format(message.timestamp.toDate())
            messageTime.text = time
            
            // Show sender name if it's a group message, get from cache if available
            if (message.groupId.isNotEmpty()) {
                val displayName = senderNames[message.sender] ?: message.sender
                senderName.text = displayName
                senderName.visibility = View.VISIBLE
            } else {
                senderName.visibility = View.GONE
            }
        }
    }
    
    /**
     * Open the media URL in a browser or appropriate app
     */
    private fun openMediaUrl(url: String, mediaType: String) {
        try {
            Log.d(TAG, "Opening media URL: $url")
            val context = recyclerView.context
            
            when (mediaType) {
                "image" -> {
                    // Use our custom image viewer for images instead of browser
                    val intent = Intent(context, ImageViewActivity::class.java).apply {
                        putExtra(ImageViewActivity.EXTRA_IMAGE_URL, url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                "document" -> {
                    // Show loading message
                    Toast.makeText(context, "Downloading document...", Toast.LENGTH_SHORT).show()
                    
                    // Download and open the document in a coroutine
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // Process URL for Google Drive links
                            val processedUrl = processGoogleDriveUrl(url)
                            Log.d(TAG, "Processed document URL: $processedUrl")
                            
                            // Download the document
                            val localUri = withContext(Dispatchers.IO) {
                                FileStorageUtils.downloadDocument(context, processedUrl)
                            }
                            
                            if (localUri != null) {
                                // Determine mime type based on URL extension
                                val fileExtension = url.substringAfterLast('.', "").lowercase()
                                Log.d(TAG, "Document file extension: $fileExtension")
                                
                                val mimeType = when (fileExtension) {
                                    "pdf" -> "application/pdf"
                                    "doc" -> "application/msword"
                                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    "txt" -> "text/plain"
                                    "xls" -> "application/vnd.ms-excel"
                                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    "ppt" -> "application/vnd.ms-powerpoint"
                                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    else -> "*/*" // Let the system decide
                                }
                                
                                Log.d(TAG, "Using MIME type: $mimeType for document")
                                
                                // Create intent to open with appropriate viewer
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(localUri, mimeType)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error opening document with specific mime type: ${e.message}", e)
                                    
                                    // Try again with generic mime type
                                    val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(localUri, "*/*")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(genericIntent)
                                }
                            } else {
                                // If download fails, try to open in browser as fallback
                                Toast.makeText(context, "Opening in browser instead...", Toast.LENGTH_SHORT).show()
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(processedUrl))
                                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(browserIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling document: ${e.message}", e)
                            Toast.makeText(context, "Error opening document: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                else -> {
                    // Default handler for unknown types
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening media URL: ${e.message}", e)
            Toast.makeText(recyclerView.context, "Could not open media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // MessageDiffCallback implementation for more efficient adapter updates
    private class MessageDiffCallback(
        private val oldList: List<Message>,
        private val newList: List<Message>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize() = oldList.size
        
        override fun getNewListSize() = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return oldItem.text == newItem.text && 
                   oldItem.sender == newItem.sender &&
                   oldItem.read == newItem.read &&
                   oldItem.timestamp == newItem.timestamp
        }
    }
    
    /**
     * Process Google Drive URLs to direct download format
     */
    private fun processGoogleDriveUrl(url: String): String {
        try {
            // URL decode in case it's encoded
            val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            Log.d(TAG, "Processing URL: $decodedUrl")
            
            // Process Google Drive links
            when {
                // Format: https://drive.google.com/file/d/FILE_ID/view?usp=drivesdk
                decodedUrl.contains("https://drive.google.com/file/d/") -> {
                    val fileId = decodedUrl.substringAfter("https://drive.google.com/file/d/").substringBefore("/")
                    // Direct download link for better reliability
                    return "https://drive.google.com/uc?export=download&id=$fileId"
                }
                // Format: https://drive.google.com/uc?id=FILE_ID
                decodedUrl.contains("https://drive.google.com/uc?id=") -> {
                    val fileId = decodedUrl.substringAfter("id=").substringBefore("&")
                    // Direct download link for better reliability
                    return "https://drive.google.com/uc?export=download&id=$fileId"
                }
                // Format: https://drive.google.com/open?id=FILE_ID
                decodedUrl.contains("https://drive.google.com/open?id=") -> {
                    val fileId = decodedUrl.substringAfter("id=").substringBefore("&")
                    // Direct download link for better reliability
                    return "https://drive.google.com/uc?export=download&id=$fileId"
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
} 