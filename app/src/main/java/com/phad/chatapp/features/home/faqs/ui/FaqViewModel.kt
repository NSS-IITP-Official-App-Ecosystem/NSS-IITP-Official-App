package com.phad.chatapp.features.home.faqs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.features.home.faqs.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FaqViewModel(
    private val repository: FaqRepository,
    private val interfaceType: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaqUiState())
    val uiState: StateFlow<FaqUiState> = _uiState.asStateFlow()

    init {
        loadFaqData()
    }

    private fun loadFaqData() {
        viewModelScope.launch {
            repository.getFaqData(interfaceType)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .collect { result ->
                    result.fold(
                        onSuccess = { (sections, subSections, questions) ->
                            _uiState.update { state ->
                                state.copy(
                                    sections = sections,
                                    subSections = subSections,
                                    questions = questions,
                                    isLoading = false,
                                    error = null
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message
                                )
                            }
                        }
                    )
                }
        }
    }

    fun navigateTo(item: FaqNavigationItem) {
        _uiState.update { state ->
            when (item) {
                is FaqNavigationItem.Section -> {
                    state.copy(
                        navigationStack = state.navigationStack + item
                    )
                }
                is FaqNavigationItem.SubSection -> {
                    state.copy(
                        navigationStack = state.navigationStack + item
                    )
                }
                is FaqNavigationItem.Question -> {
                    // For questions, add to history only if it's not the same as the last question
                    val lastQuestion = state.history.lastOrNull()
                    val shouldAddToHistory = lastQuestion?.id != item.question.id
                    
                    state.copy(
                        history = if (shouldAddToHistory) {
                            state.history + item.question
                        } else {
                            state.history // Keep the same history if it's a consecutive duplicate
                        }
                    )
                }
            }
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            state.copy(
                navigationStack = state.navigationStack.dropLast(1)
            )
        }
    }

    fun navigateToRoot() {
        _uiState.update { state ->
            state.copy(
                navigationStack = emptyList(),
                isSearchMode = false,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            val isSearchMode = query.isNotEmpty()
            val searchResults = if (isSearchMode) {
                performSearch(query, state)
            } else {
                emptyList()
            }
            
            state.copy(
                searchQuery = query,
                isSearchMode = isSearchMode,
                searchResults = searchResults
            )
        }
    }

    private fun performSearch(query: String, state: FaqUiState): List<FaqSearchResult> {
        val results = mutableListOf<FaqSearchResult>()
        val lowerQuery = query.lowercase()
        
        state.questions.forEach { (sectionId, questions) ->
            val section = state.sections[sectionId]
            if (section != null) {
                questions.forEach { question ->
                    // Search in question text
                    val questionMatches = question.question.lowercase().contains(lowerQuery)
                    
                    // Search in answer content
                    val answerMatches = when (question.answerType) {
                        AnswerType.TEXT -> {
                            val answerText = question.answer as? String ?: ""
                            answerText.lowercase().contains(lowerQuery)
                        }
                        AnswerType.BULLET_POINTS -> {
                            @Suppress("UNCHECKED_CAST")
                            val bulletPoints = question.answer as? List<String> ?: emptyList()
                            bulletPoints.any { point -> point.lowercase().contains(lowerQuery) }
                        }
                    }
                    
                    // If either question or answer contains the search term
                    if (questionMatches || answerMatches) {
                        val subSection = question.subSectionId?.let { subId ->
                            state.subSections[sectionId]?.find { it.id == subId }
                        }
                        
                        results.add(
                            FaqSearchResult(
                                question = question,
                                sectionTitle = section.title,
                                subSectionTitle = subSection?.title
                            )
                        )
                    }
                }
            }
        }
        
        return results
    }

    fun getCurrentItems(): List<FaqNavigationItem> {
        val state = _uiState.value
        
        // If in search mode, return search results as questions
        if (state.isSearchMode) {
            return state.searchResults.map { result ->
                FaqNavigationItem.Question(result.question)
            }
        }
        
        return when (val currentItem = state.navigationStack.lastOrNull()) {
            is FaqNavigationItem.Section -> {
                // Show both subsections and root-level questions together
                val subSections = state.subSections[currentItem.section.id]?.map { FaqNavigationItem.SubSection(it) } ?: emptyList()
                val rootQuestions = state.questions[currentItem.section.id]?.filter { it.subSectionId == null }?.map { FaqNavigationItem.Question(it) } ?: emptyList()
                subSections + rootQuestions
            }
            is FaqNavigationItem.SubSection -> {
                // Return questions for this sub-section
                state.questions[currentItem.subSection.sectionId]?.filter { 
                    it.subSectionId == currentItem.subSection.id 
                }?.map { 
                    FaqNavigationItem.Question(it)
                } ?: emptyList()
            }
            is FaqNavigationItem.Question, null -> {
                // At root level or showing a question, show sections
                state.sections.values.map { 
                    FaqNavigationItem.Section(it)
                }
            }
        }
    }

    fun getCurrentTitle(): String {
        return "FAQs"
    }

    class Factory(
        private val repository: FaqRepository,
        private val interfaceType: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FaqViewModel::class.java)) {
                return FaqViewModel(repository, interfaceType) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 