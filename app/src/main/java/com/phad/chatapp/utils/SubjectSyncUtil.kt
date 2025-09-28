package com.phad.chatapp.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.phad.chatapp.services.SubjectAssignmentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SubjectSyncUtil {
    private const val TAG = "SubjectSyncUtil"
    
    /**
     * Manually trigger sync of subject assignments to groups
     */
    fun syncSubjectAssignments(context: Context, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting manual sync of subject assignments")
                val service = SubjectAssignmentService()
                service.syncSubjectAssignmentsToGroups()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Subject assignments synced successfully", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during manual sync", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to sync subject assignments", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * Check if subject assignments need syncing
     */
    suspend fun needsSync(): Boolean {
        return try {
            val service = SubjectAssignmentService()
            val subjectGroups = service.getSubjectGroups()
            // Simple check: if no subject groups exist, we need sync
            subjectGroups.isEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            true // Assume sync is needed if we can't check
        }
    }
} 