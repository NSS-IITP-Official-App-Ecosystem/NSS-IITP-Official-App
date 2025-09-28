package com.phad.chatapp.adapters

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.databinding.ItemLibraryItemBinding
import com.phad.chatapp.models.LibraryItem
import androidx.core.content.ContextCompat
import com.phad.chatapp.R
import android.view.View
import android.widget.TextView
import android.widget.ImageView

class LibraryItemAdapter(private var items: List<LibraryItem>, private val listener: OnItemClickListener) : RecyclerView.Adapter<LibraryItemAdapter.LibraryItemViewHolder>() {

    // Interface for item click listener
    interface OnItemClickListener {
        fun onItemClick(item: LibraryItem)
    }

    // ViewHolder for library items
    inner class LibraryItemViewHolder(
        private val binding: ItemLibraryItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: LibraryItem) {
            binding.textViewItemTitle.text = item.title

            // Set appropriate icon based on whether it's a section or file
            if (item.isSection) {
                binding.imageViewItemIcon.setImageResource(R.drawable.ic_folder)
                binding.textViewFileLink?.visibility = View.GONE
            } else {
                binding.imageViewItemIcon.setImageResource(getFileIcon(item.mimeType))
                binding.textViewFileLink?.text = item.googleDriveLink
                binding.textViewFileLink?.visibility = View.VISIBLE
            }

            // Set different background colors for folders and files
            val backgroundColor = if (item.isSection) {
                // Folder/Section background color - Green
                ContextCompat.getColor(itemView.context, R.color.folder_background_color)
            } else {
                // File background color - Different colors based on file type
                when {
                    item.mimeType?.contains("pdf") == true -> {
                        // PDF files - Red
                        ContextCompat.getColor(itemView.context, R.color.pdf_background_color)
                    }
                    item.mimeType?.contains("image") == true -> {
                        // Image files - Purple
                        ContextCompat.getColor(itemView.context, R.color.image_background_color)
                    }
                    item.mimeType?.contains("document") == true || 
                    item.mimeType?.contains("word") == true -> {
                        // Document files - Blue
                        ContextCompat.getColor(itemView.context, R.color.document_background_color)
                    }
                    else -> {
                        // Default file color - Blue
                        ContextCompat.getColor(itemView.context, R.color.file_background_color)
                    }
                }
            }
            itemView.setBackgroundColor(backgroundColor)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val item = items[position]
                listener.onItemClick(item)
            }
        }
    }




    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryItemViewHolder {
        val binding = ItemLibraryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LibraryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<LibraryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun getFileIcon(mimeType: String?): Int {
        return R.drawable.ic_file // Default icon for unknown MIME type
    }

    fun getItems(): List<LibraryItem> {
        return items
    }
} 