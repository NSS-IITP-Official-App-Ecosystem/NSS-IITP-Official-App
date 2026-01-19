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
        private const val ARG_START_DESTINATION = "startDestination"
        
        fun newInstance(startDestination: String? = null): SchedulingFragment {
            return SchedulingFragment().apply {
                arguments = Bundle().apply {
                    startDestination?.let { putString(ARG_START_DESTINATION, it) }
                }
            }
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
        val startDestination = arguments?.getString(ARG_START_DESTINATION)
        return ComposeView(requireContext()).apply {
            setContent {
                Surface {
                    SchedulingApp(
                        startDestination = startDestination,
                        onBackClick = {
                            // Pop back to the previous fragment (ProfileFragment)
                            try {
                                androidx.navigation.Navigation.findNavController(this).popBackStack()
                            } catch (e: Exception) {
                                // Fallback
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    )
                }
            }
        }
    }
} 