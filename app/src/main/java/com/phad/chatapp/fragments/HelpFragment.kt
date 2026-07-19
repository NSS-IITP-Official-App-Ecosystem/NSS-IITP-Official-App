package com.phad.chatapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.phad.chatapp.ui.help.HelpScreen

import androidx.navigation.fragment.findNavController
import com.phad.chatapp.R
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.repositories.IssueRepository
import com.phad.chatapp.repositories.HelpRepository
import com.phad.chatapp.ui.help.AdminIssueViewModel
import com.phad.chatapp.ui.help.HelpViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class HelpFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val sessionManager = SessionManager(requireContext())
                val profile = sessionManager.getProfileFromSession()
                
                // Determine if user has any admin privileges. 
                // In a real app, userType == "admin" or "super_admin", or they have wings and a role.
                // For now, if they are admin or super admin, or if we define them as admins, we show the banner.
                val isAdmin = profile.userType.equals("admin", ignoreCase = true) || 
                              profile.userType.equals("super_admin", ignoreCase = true) ||
                              profile.userType.equals("super admin", ignoreCase = true)

                var openIssuesCount = 0

                if (isAdmin) {
                    val repository = IssueRepository(cloudinaryHelper = com.phad.chatapp.utils.CloudinaryHelper(requireContext()))
                    val adminViewModel: AdminIssueViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return AdminIssueViewModel(
                                    repository,
                                    profile.userType,
                                    profile.wings,
                                    profile.rollNumber,
                                    profile.name
                                ) as T
                            }
                        }
                    )
                    
                    val uiState by adminViewModel.uiState.collectAsState()
                    openIssuesCount = uiState.issues.size
                }

                val helpViewModel: HelpViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return HelpViewModel(HelpRepository) as T
                        }
                    }
                )
                val helpUiState by helpViewModel.uiState.collectAsState()

                HelpScreen(
                    isAdmin = isAdmin,
                    openIssuesCount = openIssuesCount,
                    userWings = profile.wings,
                    userRollNumber = profile.rollNumber ?: "",
                    helpUiState = helpUiState,
                    onRetryContacts = { helpViewModel.fetchContacts(true) },
                    onRaiseIssueClick = {
                        findNavController().navigate(R.id.action_nssHelpFragment_to_issueTrackingFragment)
                    },
                    onResolveIssuesClick = {
                        findNavController().navigate(R.id.action_nssHelpFragment_to_adminIssueTrackingFragment)
                    }
                )
            }
        }
    }
}
