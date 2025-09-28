package com.phad.chatapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class GroupsTabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a simple TextView as placeholder
        val textView = TextView(requireContext()).apply {
            text = "Groups Tab"
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        
        return textView
    }
} 