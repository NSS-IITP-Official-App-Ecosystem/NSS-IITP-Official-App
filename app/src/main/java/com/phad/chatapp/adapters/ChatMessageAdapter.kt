package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.phad.chatapp.R
import com.phad.chatapp.models.Message
import java.text.SimpleDateFormat
import java.util.Locale

class ChatMessageAdapter(private val currentUserRollNumber: String) : 
    RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {
    
    private val messages = mutableListOf<Message>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_SENT -> R.layout.item_message_sent
            else -> R.layout.item_message_received
        }
        
        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }
    
    override fun getItemCount() = messages.size
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == currentUserRollNumber) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }
    
    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
    
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.text_message_body)
        private val timeTextView: TextView = itemView.findViewById(R.id.text_message_time)
        
        fun bind(message: Message) {
            messageTextView.text = message.text
            timeTextView.text = dateFormat.format(message.timestamp.toDate())
        }
    }
} 