package com.phad.chatapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.repositories.IssueRepository
import com.phad.chatapp.ui.help.AdminIssueTrackingScreen
import com.phad.chatapp.ui.help.AdminIssueViewModel
import com.phad.chatapp.utils.SessionManager

class AdminIssueTrackingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val sessionManager = SessionManager(requireContext())
                val profile = sessionManager.getProfileFromSession()

                val repository = IssueRepository(cloudinaryHelper = com.phad.chatapp.utils.CloudinaryHelper(requireContext()))
                val viewModel: AdminIssueViewModel = viewModel(
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

                AdminIssueTrackingScreen(
                    viewModel = viewModel,
                    onBackClick = { findNavController().popBackStack() }
                )
            }
        }
    }
}
