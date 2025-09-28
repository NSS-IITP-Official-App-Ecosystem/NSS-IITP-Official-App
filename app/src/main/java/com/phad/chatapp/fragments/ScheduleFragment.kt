package com.phad.chatapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.phad.chatapp.R

class ScheduleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // This is just a placeholder for now
        val textView = view.findViewById<TextView>(R.id.text_schedule)
        textView.text = "Scheduling feature coming soon"
    }

    companion object {
        fun newInstance(): ScheduleFragment {
            return ScheduleFragment()
        }
    }
} 