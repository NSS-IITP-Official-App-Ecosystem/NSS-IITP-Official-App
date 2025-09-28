package com.phad.chatapp.features.home.faqs.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class FaqActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val interfaceType = intent.getStringExtra("interface_type") ?: "nss"
        
        setContent {
            MaterialTheme {
                FaqScreen(
                    onNavigateBack = { finish() },
                    interfaceType = interfaceType
                )
            }
        }
    }
} 