package com.phad.chatapp.ui.profile

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.phad.chatapp.MainActivity
import com.phad.chatapp.NssMainActivity
import com.phad.chatapp.R
import com.phad.chatapp.databinding.ActivityInterfaceSelectionBinding
import com.phad.chatapp.utils.SessionManager

class NssOrTeachingWingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInterfaceSelectionBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the XML layout instead of Compose
        binding = ActivityInterfaceSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        val teachingWing = intent.getBooleanExtra("teaching_wing", false)

        setupClickListeners(teachingWing)
    }

    private fun setupClickListeners(teachingWing: Boolean) {
        // NSS Button Click
        binding.btnNss.setOnClickListener {
            sessionManager.setLastInterfaceChoice("NSS")
            val intent = Intent(this, NssMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Teaching Wing Button Click
        binding.btnTeachingWing.setOnClickListener {
            sessionManager.setLastInterfaceChoice("TEACHING_WING")
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Hide Teaching Wing button if user doesn't have access
        if (!teachingWing) {
            binding.btnTeachingWing.visibility = android.view.View.GONE
        }
    }
}