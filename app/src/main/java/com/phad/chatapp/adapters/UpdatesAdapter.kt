package com.phad.chatapp.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.phad.chatapp.R
import com.phad.chatapp.models.Update
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpdatesAdapter : RecyclerView.Adapter<UpdatesAdapter.UpdateViewHolder>() {
    
    private var updates: List<Update> = listOf()
    
    fun updateList(newUpdates: List<Update>) {
        this.updates = newUpdates
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_update, parent, false)
        return UpdateViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: UpdateViewHolder, position: Int) {
        holder.bind(updates[position])
    }
    
    override fun getItemCount(): Int = updates.size
    
    inner class UpdateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorImage: CircleImageView = itemView.findViewById(R.id.author_image)
        private val authorName: TextView = itemView.findViewById(R.id.author_name)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val content: TextView = itemView.findViewById(R.id.content)
        private val mediaContainer: CardView = itemView.findViewById(R.id.media_container)
        private val mediaImage: ImageView = itemView.findViewById(R.id.media_image)
        private val documentContainer: CardView = itemView.findViewById(R.id.document_container)
        private val documentName: TextView = itemView.findViewById(R.id.document_name)
        private val downloadButton: MaterialButton = itemView.findViewById(R.id.download_button)
        
        fun bind(update: Update) {
            // Set author details
            authorName.text = update.authorName
            timestamp.text = getTimeAgo(update.timestamp)
            content.text = update.content
            
            // Load author image if available
            if (!update.authorImageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(update.authorImageUrl)
                    .placeholder(R.drawable.default_profile_image)
                    .error(R.drawable.default_profile_image)
                    .into(authorImage)
            } else {
                authorImage.setImageResource(R.drawable.default_profile_image)
            }
            
            // Handle media image if available
            if (!update.mediaUrl.isNullOrEmpty()) {
                mediaContainer.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(update.mediaUrl)
                    .into(mediaImage)
                
                // Set click listener to view image in full screen
                mediaImage.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.mediaUrl))
                    itemView.context.startActivity(intent)
                }
            } else {
                mediaContainer.visibility = View.GONE
            }
            
            // Handle document if available
            if (!update.documentUrl.isNullOrEmpty()) {
                documentContainer.visibility = View.VISIBLE
                documentName.text = update.documentName ?: "Document"
                
                // Set click listener to download/view document
                downloadButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.documentUrl))
                    itemView.context.startActivity(intent)
                }
            } else {
                documentContainer.visibility = View.GONE
            }
        }
        
        private fun getTimeAgo(timestamp: Long): String {
            val now = Date().time
            val diff = now - timestamp
            
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hr ago"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
} 