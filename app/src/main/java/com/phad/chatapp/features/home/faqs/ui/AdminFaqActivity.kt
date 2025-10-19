package com.phad.chatapp.features.home.faqs.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.phad.chatapp.utils.SessionManager

class AdminFaqActivity : ComponentActivity() {
    
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        // Get user information from intent or session
        val userType = intent.getStringExtra("user_type") ?: sessionManager.fetchUserType()
        
        setContent {
            MaterialTheme {
                AdminFaqScreen(
                    onNavigateBack = { finish() },
                    userType = userType
                )
            }
        }
    }
}
