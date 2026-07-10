package com.phad.chatapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.phad.chatapp.repositories.IssueRepository
import com.phad.chatapp.ui.help.IssueTrackingScreen
import com.phad.chatapp.ui.help.IssueViewModel
import com.phad.chatapp.utils.CloudinaryHelper
import com.phad.chatapp.utils.SessionManager
import androidx.lifecycle.ViewModel

class IssueTrackingFragment : Fragment() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val userName = (sessionManager.getUserDetails()[SessionManager.KEY_USER_NAME] as? String) ?: "Unknown"
                val userRollNumber = (sessionManager.getUserDetails()[SessionManager.KEY_USER_ROLL_NUMBER] as? String) ?: "Unknown"
                val userWings = sessionManager.getProfileFromSession().wings

                val viewModel: IssueViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val repository = IssueRepository(
                                cloudinaryHelper = CloudinaryHelper.getInstance(requireContext())
                            )
                            return IssueViewModel(repository, userRollNumber, userName) as T
                        }
                    }
                )

                IssueTrackingScreen(
                    viewModel = viewModel,
                    userName = userName,
                    userRollNumber = userRollNumber,
                    userWings = userWings,
                    onBackClick = { findNavController().popBackStack() }
                )
            }
        }
    }
}
