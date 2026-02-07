package com.phad.chatapp.adapters

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.phad.chatapp.R
import java.io.File

sealed class AttachmentItem {
    data class Local(val uri: Uri) : AttachmentItem()
    data class Remote(val url: String, val name: String? = null) : AttachmentItem()
}

class AttachmentAdapter(
    private val items: MutableList<AttachmentItem>,
    private val isDocument: Boolean,
    private val onRemoveClick: (Int) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<AttachmentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.previewImage)
        val documentLayout: LinearLayout = view.findViewById(R.id.docPreviewLayout)
        val documentName: TextView = view.findViewById(R.id.fileName)
        val removeButton: ImageButton = view.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attachment_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        if (isDocument) {
            holder.imageView.visibility = View.GONE
            holder.documentLayout.visibility = View.VISIBLE
            
            holder.documentName.text = when (item) {
                is AttachmentItem.Local -> getFileName(item.uri)
                is AttachmentItem.Remote -> item.name ?: getFileNameFromUrl(item.url)
            }
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.documentLayout.visibility = View.GONE

            val loadModel: Any = when (item) {
                is AttachmentItem.Local -> item.uri
                is AttachmentItem.Remote -> item.url
            }

            Glide.with(context)
                .load(loadModel)
                .centerCrop()
                .into(holder.imageView)
        }

        holder.removeButton.setOnClickListener {
            onRemoveClick(holder.adapterPosition)
        }
    }

    override fun getItemCount() = items.size

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Document"
    }

    private fun getFileNameFromUrl(url: String): String {
         val cut = url.lastIndexOf('/')
         var fileName = if (cut != -1) {
             url.substring(cut + 1)
         } else {
             "Document"
         }
         // Remove query params if any
         val queryIndex = fileName.indexOf('?')
         if (queryIndex != -1) {
             fileName = fileName.substring(0, queryIndex)
         }
         return fileName
    }
}
