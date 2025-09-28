package com.phad.chatapp.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.ChatActivity
import com.phad.chatapp.GroupChatActivity
import com.phad.chatapp.R
import com.phad.chatapp.models.UnreadMessage
import com.phad.chatapp.utils.SessionManager
import java.text.SimpleDateFormat
import java.util.Locale

class UnreadMessageAdapter(
    private val context: Context,
    private val isGroupMessages: Boolean
) : RecyclerView.Adapter<UnreadMessageAdapter.UnreadMessageViewHolder>() {
    
    private var messages: List<UnreadMessage> = listOf()
    private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    fun updateMessages(newMessages: List<UnreadMessage>) {
        this.messages = newMessages
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnreadMessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unread_message, parent, false)
        return UnreadMessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: UnreadMessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }
    
    override fun getItemCount(): Int = messages.size
    
    inner class UnreadMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.text_sender_name)
        private val textMessagePreview: TextView = itemView.findViewById(R.id.text_message_content)
        private val textTimestamp: TextView = itemView.findViewById(R.id.text_timestamp)
        private val unreadIndicator: View = itemView.findViewById(R.id.unread_indicator)
        
        init {
            // Set click listener on the message item
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = messages[position]
                    navigateToChat(message)
                }
            }
        }
        
        fun bind(message: UnreadMessage) {
            textName.text = message.senderName
            textMessagePreview.text = message.messageText
            
            // Format and display timestamp
            val formattedTime = dateFormat.format(message.timestamp.toDate())
            textTimestamp.text = formattedTime
            
            // Set the indicator color based on message type
            unreadIndicator.setBackgroundColor(
                context.resources.getColor(
                    if (message.isGroupMessage) R.color.colorAccent else R.color.colorPrimary,
                    null
                )
            )
        }
        
        private fun navigateToChat(message: UnreadMessage) {
            val intent = if (message.isGroupMessage) {
                Intent(context, GroupChatActivity::class.java).apply {
                    putExtra("GROUP_ID", message.groupId)
                    putExtra("GROUP_NAME", message.senderName)
                }
            } else {
                Intent(context, ChatActivity::class.java).apply {
                    val sessionManager = SessionManager(context)
                    putExtra("currentUserRollNumber", sessionManager.fetchRollNumber())
                    putExtra("otherUserRollNumber", message.chatPartnerId)
                    putExtra("otherUserName", message.senderName)
                }
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
} 