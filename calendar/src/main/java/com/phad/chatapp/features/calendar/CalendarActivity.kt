package com.phad.chatapp.features.calendar

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.phad.chatapp.features.calendar.ui.CalendarFragment

class CalendarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.calendar_container, CalendarFragment.newInstance())
                .commitNow()
        }
    }
} 