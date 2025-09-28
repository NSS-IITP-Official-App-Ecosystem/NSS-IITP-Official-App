package com.phad.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.phad.chatapp.ui.events.EventsListScreen

class EventsListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val semester = intent.getIntExtra("semester", 1)
        val rollNumber = intent.getStringExtra("rollNumber") ?: ""
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EventsListScreen(
                        semester = semester,
                        rollNumber = rollNumber,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}
