package com.phad.chatapp.ui.help

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.models.Issue
import com.phad.chatapp.models.IssueStatus
import com.phad.chatapp.repositories.IssueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class IssueUiState(
    val openIssues: List<Issue> = emptyList(),
    val closedIssues: List<Issue> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val isClosing: Boolean = false,
    val closeSuccess: Boolean = false
)

class IssueViewModel(
    private val repository: IssueRepository,
    private val rollNumber: String,
    private val userName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueUiState())
    val uiState: StateFlow<IssueUiState> = _uiState.asStateFlow()

    init {
        fetchIssues()
    }

    private fun fetchIssues() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getIssuesForUser(rollNumber)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to fetch issues"
                    )
                }
                .collect { issues ->
                    val openIssues = issues.filter { it.status == IssueStatus.OPEN }
                    val closedIssues = issues.filter { it.status == IssueStatus.CLOSED }
                    
                    _uiState.value = _uiState.value.copy(
                        openIssues = openIssues,
                        closedIssues = closedIssues,
                        isLoading = false
                    )
                }
        }
    }

    fun submitIssue(issue: Issue, imageUri: Uri?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submitSuccess = false, error = null)
            val result = repository.submitIssue(issue, imageUri)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to submit issue"
                )
            }
        }
    }

    fun closeIssue(issueId: String, comment: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClosing = true, closeSuccess = false, error = null)
            val result = repository.closeIssue(issueId, rollNumber, userName, comment)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isClosing = false, closeSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isClosing = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to close issue"
                )
            }
        }
    }

    fun resetSubmitState() {
        _uiState.value = _uiState.value.copy(submitSuccess = false, closeSuccess = false, error = null)
    }
}
