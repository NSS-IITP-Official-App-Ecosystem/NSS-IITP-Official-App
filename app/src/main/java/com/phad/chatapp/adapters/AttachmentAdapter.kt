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

class AttachmentAdapter(
    private val items: MutableList<Uri>,
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
        val uri = items[position]

        if (isDocument) {
            holder.imageView.visibility = View.GONE
            holder.documentLayout.visibility = View.VISIBLE
            
            holder.documentName.text = getFileName(uri)
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.documentLayout.visibility = View.GONE

            Glide.with(context)
                .load(uri)
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
}
