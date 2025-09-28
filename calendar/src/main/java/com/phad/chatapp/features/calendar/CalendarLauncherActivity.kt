package com.phad.chatapp.features.calendar

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.phad.chatapp.features.calendar.databinding.ActivityCalendarLauncherBinding
import com.phad.chatapp.features.calendar.ui.CalendarFragment
import com.phad.chatapp.features.calendar.utils.CalendarSessionManager

class CalendarLauncherActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCalendarLauncherBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Log the launch of the activity
        Log.d("CalendarLauncher", "Calendar module launched independently")
        
        // Get user type from extras if available (for testing)
        val userType = intent.getStringExtra("USER_TYPE") ?: ""
        if (userType.isNotEmpty()) {
            // Set the override user type in our SessionManager
            val sessionManager = CalendarSessionManager(this)
            sessionManager.setOverrideUserType(userType)
            
            Toast.makeText(this, "User type set to: $userType", Toast.LENGTH_SHORT).show()
            Log.d("CalendarLauncher", "User type set to: $userType")
        }
        
        // Add the CalendarFragment if this is the first creation
        if (savedInstanceState == null) {
            val fragment = CalendarFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
} 