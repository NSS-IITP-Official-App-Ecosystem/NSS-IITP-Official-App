package com.phad.chatapp.features.scheduling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

/**
 * Main fragment for the Scheduling feature.
 * This is the integration point between ChatApp_Standalone and The Phad Project scheduling functionality.
 */
class SchedulingFragment : Fragment() {

    companion object {
        fun newInstance(): SchedulingFragment {
            return SchedulingFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the scheduling module
        SchedulingInitializer.initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Surface {
                    SchedulingApp()
                }
            }
        }
    }
} 