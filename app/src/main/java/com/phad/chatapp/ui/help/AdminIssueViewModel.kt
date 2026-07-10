package com.phad.chatapp.ui.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.models.Issue
import com.phad.chatapp.repositories.IssueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AdminIssueUiState(
    val issues: List<Issue> = emptyList(),
    val isLoading: Boolean = true,
    val isResolving: Boolean = false,
    val resolveError: String? = null,
    val resolveSuccess: Boolean = false
)

class AdminIssueViewModel(
    private val repository: IssueRepository,
    private val adminUserType: String,
    private val adminWings: List<String>,
    private val adminRollNumber: String,
    private val adminName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminIssueUiState())
    val uiState: StateFlow<AdminIssueUiState> = _uiState.asStateFlow()

    init {
        fetchAdminIssues()
    }

    private fun fetchAdminIssues() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getAdminOpenIssues(adminUserType, adminWings)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resolveError = e.message ?: "Failed to fetch issues"
                    )
                }
                .collect { issueList ->
                    _uiState.value = _uiState.value.copy(
                        issues = issueList,
                        isLoading = false
                    )
                }
        }
    }

    fun resolveIssue(issueId: String, comment: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolving = true, resolveSuccess = false, resolveError = null)
            val result = repository.resolveIssue(issueId, adminRollNumber, adminName, comment)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isResolving = false, resolveSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolveError = result.exceptionOrNull()?.message ?: "Failed to resolve issue"
                )
            }
        }
    }

    fun resetResolveState() {
        _uiState.value = _uiState.value.copy(resolveError = null, resolveSuccess = false)
    }
}
