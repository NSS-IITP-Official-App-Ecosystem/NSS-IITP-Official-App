package com.phad.chatapp.ui.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.models.ContactGroup
import com.phad.chatapp.repositories.HelpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HelpUiState(
    val contactGroups: List<ContactGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HelpViewModel(
    private val repository: HelpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpUiState())
    val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

    init {
        fetchContacts()
    }

    fun fetchContacts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getContactGroups(forceRefresh)
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    contactGroups = result.getOrNull() ?: emptyList(),
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load contacts"
                )
            }
        }
    }
}
