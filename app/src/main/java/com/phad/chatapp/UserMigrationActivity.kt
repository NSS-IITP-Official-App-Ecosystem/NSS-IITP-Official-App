package com.phad.chatapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.UserDataMigrator
import kotlinx.coroutines.launch

class UserMigrationActivity : AppCompatActivity() {
    private lateinit var buttonStartMigration: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewStatus: TextView
    private lateinit var sessionManager: SessionManager
    
    private var isMigrationRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SessionManager
        sessionManager = SessionManager(this)
        
        // Check if the user has Admin permissions - handle multiple admin types
        val userType = sessionManager.fetchUserType()
        val isAdmin = userType == "Admin" || userType == "Admin1" || userType == "Admin2"
        if (!isAdmin) {
            // Show error and finish activity if not an Admin user
            AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("Only Admin users can access the User Migration tool.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }
        
        setContentView(R.layout.activity_user_migration)
        
        // Initialize views
        buttonStartMigration = findViewById(R.id.button_start_migration)
        progressBar = findViewById(R.id.progress_bar)
        textViewStatus = findViewById(R.id.text_view_status)
        
        // Set up button click listener
        buttonStartMigration.setOnClickListener {
            if (!isMigrationRunning) {
                showConfirmationDialog()
            }
        }
    }
    
    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Start User Migration")
            .setMessage("This will migrate all users from the TWApp database to the main app's users collection. This operation may overwrite existing user data. Are you sure you want to continue?")
            .setPositiveButton("Yes") { _, _ ->
                startMigration()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun startMigration() {
        // Update UI state
        isMigrationRunning = true
        buttonStartMigration.isEnabled = false
        progressBar.visibility = View.VISIBLE
        textViewStatus.visibility = View.VISIBLE
        textViewStatus.text = "Initializing migration..."
        
        // Start the migration process in the background
        lifecycleScope.launch {
            try {
                UserDataMigrator.migrateAllUsers(
                    context = this@UserMigrationActivity,
                    lifecycleScope = lifecycleScope,
                    onProgress = { message, count ->
                        // Update the UI with progress
                        runOnUiThread {
                            textViewStatus.text = message
                        }
                    },
                    onComplete = { totalCount ->
                        // Migration finished successfully
                        runOnUiThread {
                            migrationCompleted(totalCount)
                        }
                    },
                    onError = { error ->
                        // Handle error
                        runOnUiThread {
                            migrationFailed(error)
                        }
                    }
                )
            } catch (e: Exception) {
                // Handle any unexpected errors
                runOnUiThread {
                    migrationFailed(e)
                }
            }
        }
    }
    
    private fun migrationCompleted(totalCount: Int) {
        // Update UI state
        isMigrationRunning = false
        buttonStartMigration.isEnabled = true
        progressBar.visibility = View.GONE
        
        // Show completion message
        textViewStatus.text = "Migration completed successfully! Total records migrated: $totalCount"
        
        // Show a completion dialog
        AlertDialog.Builder(this)
            .setTitle("Migration Complete")
            .setMessage("Successfully migrated $totalCount users from TWApp to the main app's users collection.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun migrationFailed(error: Exception) {
        // Update UI state
        isMigrationRunning = false
        buttonStartMigration.isEnabled = true
        progressBar.visibility = View.GONE
        
        // Show error message
        textViewStatus.text = "Migration failed: ${error.message}"
        
        // Show an error dialog
        AlertDialog.Builder(this)
            .setTitle("Migration Failed")
            .setMessage("An error occurred during the migration process: ${error.message}")
            .setPositiveButton("OK", null)
            .show()
    }
} 