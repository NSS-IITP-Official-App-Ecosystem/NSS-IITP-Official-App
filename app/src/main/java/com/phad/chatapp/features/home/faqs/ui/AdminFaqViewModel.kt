package com.phad.chatapp.features.home.faqs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phad.chatapp.features.home.faqs.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminFaqViewModel(
    private val repository: FaqRepository,
    private val userType: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminFaqUiState())
    val uiState: StateFlow<AdminFaqUiState> = _uiState.asStateFlow()

    private val _dialogState = MutableStateFlow(AdminDialogState())
    val dialogState: StateFlow<AdminDialogState> = _dialogState.asStateFlow()

    init {
        loadRootSections()
    }

    fun refresh() {
        loadRootSections()
    }

    fun refreshCurrent() {
        val lastNode = _uiState.value.navigationStack.lastOrNull()
        if (lastNode != null) {
            // Use the same loading logic as navigateBack for consistency
            loadSectionContentForNode(lastNode, _uiState.value.navigationStack)
        } else {
            refresh()
        }
    }

    private fun loadRootSections() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                repository.getFaqData().collect { result ->
                    result.fold(
                        onSuccess = { (sections, _, _) ->
                            val rootSections = sections.values.toList()
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    currentUserType = userType,
                                    rootSections = rootSections,
                                    error = null
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Failed to load FAQ data"
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    fun showOperationDialog(node: FaqNode) {
        _uiState.update { 
            it.copy(
                showOperationDialog = true,
                selectedNode = node
            )
        }
    }

    fun hideOperationDialog() {
        _uiState.update { 
            it.copy(
                showOperationDialog = false,
                operationType = null
            )
        }
    }

    fun selectOperation(operation: AdminOperation) {
        val selectedNode = _uiState.value.selectedNode ?: return
        
        when (operation) {
            AdminOperation.OPEN_SECTION -> {
                openSection(selectedNode)
                hideOperationDialog()
            }
            AdminOperation.EDIT_SECTION_NAME -> {
                showEditDialog(selectedNode, operation)
            }
            AdminOperation.ADD_SUBSECTION -> {
                showAddDialog(selectedNode, operation)
            }
            AdminOperation.ADD_QUESTION -> {
                showAddDialog(selectedNode, operation)
            }
            AdminOperation.DELETE_SECTION -> {
                showDeleteConfirmation(selectedNode)
            }
            AdminOperation.EDIT_QUESTION -> {
                showEditDialog(selectedNode, operation)
            }
            AdminOperation.DELETE_QUESTION -> {
                showDeleteConfirmation(selectedNode)
            }
        }
        
        _uiState.update { it.copy(operationType = operation) }
    }

    private fun showEditDialog(node: FaqNode, operation: AdminOperation) {
        val formData = when (operation) {
            AdminOperation.EDIT_SECTION_NAME -> {
                AdminFormData(title = node.title)
            }
            AdminOperation.EDIT_QUESTION -> {
                val question = node.question
                val prefilledAnswer = when (question?.answerType) {
                    AnswerType.TEXT, null -> question?.answer as? String ?: ""
                    else -> ""
                }
                AdminFormData(
                    question = question?.question ?: "",
                    answerType = AnswerType.TEXT,
                    textAnswer = prefilledAnswer,
                    bulletPoints = emptyList()
                )
            }
            else -> AdminFormData()
        }

        _dialogState.update {
            AdminDialogState(
                isVisible = true,
                title = AdminOperations.getOperationDisplayName(operation),
                operation = operation,
                node = node,
                formData = formData
            )
        }
        hideOperationDialog()
    }

    private fun showAddDialog(node: FaqNode, operation: AdminOperation) {
        _dialogState.update {
            AdminDialogState(
                isVisible = true,
                title = AdminOperations.getOperationDisplayName(operation),
                operation = operation,
                node = node,
                formData = AdminFormData()
            )
        }
        hideOperationDialog()
    }

    private fun showDeleteConfirmation(node: FaqNode) {
        _uiState.update { 
            it.copy(
                showDeleteConfirmation = true,
                selectedNode = node
            )
        }
        hideOperationDialog()
    }

    fun hideDialog() {
        _dialogState.update { AdminDialogState() }
    }

    fun hideDeleteConfirmation() {
        _uiState.update {
            it.copy(
                showDeleteConfirmation = false,
                selectedNode = null
            )
        }
    }

    private fun openSection(node: FaqNode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val sectionId = when (node.type) {
                    FaqNodeType.ROOT_SECTION -> node.id
                    FaqNodeType.SUBSECTION -> node.id  // Use the subsection's own ID, not its parent
                    FaqNodeType.QUESTION -> node.sectionId ?: node.id
                }
                
                // Use getSectionContent to get direct children of the current section
                val result = repository.getSectionContent(sectionId)
                result.fold(
                    onSuccess = { sectionContent ->
                        // Auto-select tab based on content
                        val autoSelectedTab = when {
                            sectionContent.subSections.isNotEmpty() && sectionContent.questions.isNotEmpty() -> "subsections"
                            sectionContent.subSections.isNotEmpty() -> "subsections"
                            sectionContent.questions.isNotEmpty() -> "questions"
                            else -> "subsections" // default
                        }
                        
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentSectionContent = sectionContent,
                                navigationStack = it.navigationStack + node,
                                currentTab = autoSelectedTab,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load section content"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            if (state.navigationStack.isNotEmpty()) {
                val newNavigationStack = state.navigationStack.dropLast(1)
                if (newNavigationStack.isNotEmpty()) {
                    // If there are still items in the navigation stack, load the previous section's content
                    val previousNode = newNavigationStack.last()
                    loadSectionContentForNode(previousNode, newNavigationStack)
                    state.copy(navigationStack = newNavigationStack)
                } else {
                    // If navigation stack is empty, go back to main FAQ management screen
                    state.copy(
                        navigationStack = emptyList(),
                        currentSectionContent = null
                    )
                }
            } else {
                state
            }
        }
    }

    private fun loadSectionContentForNode(node: FaqNode, navigationStack: List<FaqNode>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val sectionId = when (node.type) {
                    FaqNodeType.ROOT_SECTION -> node.id
                    FaqNodeType.SUBSECTION -> node.id  // Use the subsection's own ID, not its parent
                    FaqNodeType.QUESTION -> node.sectionId ?: node.id
                }
                
                // Use getSectionContent to get direct children of the current section
                val result = repository.getSectionContent(sectionId)
                result.fold(
                    onSuccess = { sectionContent ->
                        // Auto-select tab based on content
                        val autoSelectedTab = when {
                            sectionContent.subSections.isNotEmpty() && sectionContent.questions.isNotEmpty() -> "subsections"
                            sectionContent.subSections.isNotEmpty() -> "subsections"
                            sectionContent.questions.isNotEmpty() -> "questions"
                            else -> "subsections" // default
                        }
                        
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentSectionContent = sectionContent,
                                navigationStack = navigationStack,
                                currentTab = autoSelectedTab,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load section content"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    fun navigateToRoot() {
        _uiState.update {
            it.copy(
                navigationStack = emptyList(),
                currentSectionContent = null
            )
        }
    }

    fun openRootSection(section: FaqSection) {
        val node = FaqNode(
            id = section.id,
            title = section.title,
            type = FaqNodeType.ROOT_SECTION,
            section = section
        )
        openSection(node)
    }

    fun openSubSection(subSection: FaqSubSection) {
        val subSectionNode = FaqNode(
            id = subSection.id,
            title = subSection.title,
            type = FaqNodeType.SUBSECTION,
            sectionId = subSection.sectionId,
            subSectionId = subSection.id,
            subSection = subSection
        )
        openSection(subSectionNode)
    }

    fun openQuestion(question: FaqQuestion) {
        val questionNode = FaqNode(
            id = question.id,
            title = question.question,
            type = FaqNodeType.QUESTION,
            sectionId = question.sectionId,
            subSectionId = question.subSectionId,
            question = question
        )
        showEditDialog(questionNode, AdminOperation.EDIT_QUESTION)
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun editSectionName(node: FaqNode) {
        showEditDialog(node, AdminOperation.EDIT_SECTION_NAME)
    }

    fun deleteSection(node: FaqNode) {
        showDeleteConfirmation(node)
    }

    fun addSubSection(node: FaqNode) {
        showAddDialog(node, AdminOperation.ADD_SUBSECTION)
    }

    fun addQuestion(node: FaqNode) {
        showAddDialog(node, AdminOperation.ADD_QUESTION)
    }

    fun updateFormData(formData: AdminFormData) {
        _dialogState.update { it.copy(formData = formData) }
    }

    fun executeOperation() {
        val dialogState = _dialogState.value
        val operation = dialogState.operation ?: return
        val node = dialogState.node ?: return
        val formData = dialogState.formData

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = when (operation) {
                    AdminOperation.EDIT_SECTION_NAME -> {
                        // If node is subsection, update subsection; else update section
                        if (node.type == FaqNodeType.SUBSECTION && node.subSection != null) {
                            val updatedSub = node.subSection.copy(title = formData.title)
                            repository.updateSubSection(updatedSub)
                        } else {
                            val section = node.section?.copy(title = formData.title) ?: return@launch
                            repository.updateSection(section)
                        }
                    }
                    AdminOperation.OPEN_SECTION -> {
                        // This operation is handled elsewhere, return success
                        Result.success(Unit)
                    }
                    AdminOperation.ADD_SUBSECTION -> {
                        // Determine the root section ID for proper hierarchy
                        val rootSectionId = when (node.type) {
                            FaqNodeType.SUBSECTION -> {
                                // For subsections, we need to find the root section
                                // This should be the sectionId of the subsection
                                node.sectionId ?: node.id
                            }
                            FaqNodeType.ROOT_SECTION -> node.id
                            else -> node.id
                        }
                        
                        val subSection = FaqSubSection(
                            id = repository.generateId(),
                            sectionId = rootSectionId,
                            title = formData.title,
                            description = null,
                            parentSubSectionId = if (node.type == FaqNodeType.SUBSECTION) {
                                // If we're adding a subsection to a subsection, set the parent
                                node.id
                            } else {
                                // If we're adding a subsection to a root section, no parent
                                null
                            }
                        )
                        repository.addSubSection(subSection)
                    }
                    AdminOperation.ADD_QUESTION -> {
                        val question = FaqQuestion(
                            id = repository.generateId(),
                            sectionId = node.sectionId ?: node.id,
                            subSectionId = node.subSectionId,
                            question = formData.question,
                            answerType = AnswerType.TEXT,
                            answer = formData.textAnswer
                        )
                        repository.addQuestion(question)
                    }
                    AdminOperation.DELETE_SECTION -> {
                        // This operation is handled in executeDelete(), return success
                        Result.success(Unit)
                    }
                    AdminOperation.EDIT_QUESTION -> {
                        val existingQuestion = node.question ?: return@launch
                        val updatedQuestion = existingQuestion.copy(
                            question = formData.question,
                            answerType = AnswerType.TEXT,
                            answer = formData.textAnswer
                        )
                        repository.updateQuestion(updatedQuestion)
                    }
                    AdminOperation.DELETE_QUESTION -> {
                        // This operation is handled in executeDelete(), return success
                        Result.success(Unit)
                    }
                }

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                        hideDialog()
                        refreshCurrent()
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Operation failed"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    fun executeDelete() {
        val selectedNode = _uiState.value.selectedNode ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val result = when (selectedNode.type) {
                    FaqNodeType.ROOT_SECTION, FaqNodeType.SUBSECTION -> {
                        if (selectedNode.type == FaqNodeType.SUBSECTION && selectedNode.subSectionId != null) {
                            repository.deleteSubSection(selectedNode.sectionId ?: "", selectedNode.subSectionId)
                        } else {
                            repository.deleteSection(selectedNode.id)
                        }
                    }
                    FaqNodeType.QUESTION -> {
                        repository.deleteQuestion(selectedNode.sectionId ?: "", selectedNode.id)
                    }
                }

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                        hideDeleteConfirmation()
                        refreshCurrent()
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Delete operation failed"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: FaqRepository,
        private val userType: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AdminFaqViewModel::class.java)) {
                return AdminFaqViewModel(repository, userType) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
