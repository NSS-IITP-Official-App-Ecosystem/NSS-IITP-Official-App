package com.phad.chatapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.phad.chatapp.databinding.ItemParticipantBinding
import com.phad.chatapp.models.User

class ParticipantAdapter(
    private var allUsers: List<User> = emptyList(),
    private val selectedParticipants: MutableSet<String> = mutableSetOf(),
    private val lockedParticipants: Set<String> = emptySet(),
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {

    // Keep a filtered list of users for display
    private var filteredUsers: List<User> = allUsers

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val binding = ItemParticipantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParticipantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val user = filteredUsers[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = filteredUsers.size

    fun updateParticipants(newUsers: List<User>) {
        // Update both all users and filtered users
        allUsers = newUsers
        filteredUsers = newUsers
        notifyDataSetChanged()
    }
    
    /**
     * Get all participants count (including those filtered out)
     */
    fun getOriginalParticipantCount(): Int {
        return allUsers.size
    }
    
    /**
     * Filter participants based on search query
     */
    fun filterParticipants(query: String) {
        if (query.isEmpty()) {
            resetFilter()
            return
        }
        
        val lowerCaseQuery = query.lowercase()
        filteredUsers = allUsers.filter { user ->
            user.name.lowercase().contains(lowerCaseQuery) || 
            user.rollNumber.lowercase().contains(lowerCaseQuery)
        }
        notifyDataSetChanged()
    }
    
    /**
     * Reset filter to show all participants
     */
    fun resetFilter() {
        filteredUsers = allUsers
        notifyDataSetChanged()
    }
    
    fun getSelectedParticipants(): List<String> {
        return selectedParticipants.toList()
    }
    
    fun getSelectedCount(): Int {
        return selectedParticipants.size
    }
    
    fun ensureUserSelected(userId: String) {
        if (!selectedParticipants.contains(userId)) {
            selectedParticipants.add(userId)
            // Notify adapter to refresh the view if needed
            notifyDataSetChanged()
        }
    }

    /**
     * Select all participants (except locked ones)
     */
    fun selectAllParticipants() {
        var hasChanges = false
        filteredUsers.forEach { user ->
            val participantId = user.rollNumber
            if (!lockedParticipants.contains(participantId) && !selectedParticipants.contains(participantId)) {
                selectedParticipants.add(participantId)
                hasChanges = true
            }
        }
        if (hasChanges) {
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedParticipants.size)
        }
    }

    /**
     * Clear all selections (except locked ones)
     */
    fun clearAllSelections() {
        var hasChanges = false
        val participantsToRemove = selectedParticipants.filter { participantId ->
            !lockedParticipants.contains(participantId)
        }
        
        participantsToRemove.forEach { participantId ->
            selectedParticipants.remove(participantId)
            hasChanges = true
        }
        
        if (hasChanges) {
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedParticipants.size)
        }
    }

    inner class ParticipantViewHolder(private val binding: ItemParticipantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.userName.text = user.name
            binding.userRollNumber.text = user.rollNumber

            // Use rollNumber as the identifier for selection
            val participantId = user.rollNumber
            
            // Check if this participant is locked (creator or current user)
            val isLocked = lockedParticipants.contains(participantId)
            
            // Set checkbox state without triggering listener
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = selectedParticipants.contains(participantId)
            binding.checkbox.isEnabled = !isLocked
            
            // Optional visual indicator for locked users
            if (isLocked) {
                binding.userName.alpha = 1.0f
                binding.userRollNumber.alpha = 1.0f
            } else {
                binding.userName.alpha = if (binding.checkbox.isChecked) 1.0f else 0.7f
                binding.userRollNumber.alpha = if (binding.checkbox.isChecked) 1.0f else 0.7f
            }

            // Set click listeners only if not locked
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (!isLocked) {
                    if (isChecked) {
                        selectedParticipants.add(participantId)
                    } else {
                        selectedParticipants.remove(participantId)
                    }
                    
                    // Update the UI for selected state
                    binding.userName.alpha = if (isChecked) 1.0f else 0.7f
                    binding.userRollNumber.alpha = if (isChecked) 1.0f else 0.7f
                    
                    // Notify listener
                    onSelectionChanged?.invoke(selectedParticipants.size)
                }
            }

            // Make the whole item clickable to toggle the checkbox
            binding.root.setOnClickListener {
                if (!isLocked) {
                    binding.checkbox.isChecked = !binding.checkbox.isChecked
                }
            }
        }
    }
} 