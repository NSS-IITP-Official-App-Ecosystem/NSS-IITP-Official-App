package com.phad.chatapp.features.scheduling.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.phad.chatapp.features.scheduling.SchedulingApp
import com.phad.chatapp.features.scheduling.SchedulingInitializer

/**
 * Test activity for independently testing the scheduling module.
 * This is not meant to be used in production, only for development and testing.
 */
class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the scheduling module
        SchedulingInitializer.initialize()
        
        // Set the content to the SchedulingApp
        setContent {
            SchedulingApp()
        }
    }
} 