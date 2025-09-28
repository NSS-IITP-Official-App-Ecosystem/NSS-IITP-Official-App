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
    private val userType: String,
    private val isTeachingWing: Boolean
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
        val current = _uiState.value.currentSectionContent
        val lastNode = _uiState.value.navigationStack.lastOrNull()
        if (current != null && lastNode != null) {
            val sectionId = when (lastNode.type) {
                FaqNodeType.ROOT_SECTION -> lastNode.id
                FaqNodeType.SUBSECTION, FaqNodeType.QUESTION -> lastNode.sectionId ?: lastNode.id
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    val result = repository.getSectionContent(sectionId)
                    result.fold(
                        onSuccess = { sectionContent ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    currentSectionContent = sectionContent,
                                    error = null
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Failed to refresh"
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
        } else {
            refresh()
        }
    }

    private fun loadRootSections() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val isAdmin = userType.startsWith("Admin")
                val typesToLoad = when {
                    isTeachingWing && isAdmin -> listOf("nss", "teaching_wing")
                    else -> listOf("nss")
                }

                val aggregatedSections = mutableMapOf<String, FaqSection>()
                for (type in typesToLoad) {
                    repository.getFaqData(type).collect { result ->
                        result.fold(
                            onSuccess = { (sections, _, _) ->
                                aggregatedSections.putAll(sections)
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
                }

                val rootSections = aggregatedSections.values.toList()
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        currentUserType = userType,
                        isTeachingWing = isTeachingWing,
                        rootSections = rootSections,
                        error = null
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
                AdminFormData(
                    question = question?.question ?: "",
                    answerType = question?.answerType ?: AnswerType.TEXT,
                    textAnswer = if (question?.answerType == AnswerType.TEXT) 
                        question.answer as? String ?: "" else "",
                    bulletPoints = if (question?.answerType == AnswerType.BULLET_POINTS)
                        (question.answer as? List<*>)?.mapNotNull { it as? String } ?: emptyList() else emptyList()
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
                    FaqNodeType.SUBSECTION -> node.sectionId ?: node.id
                    FaqNodeType.QUESTION -> node.sectionId ?: node.id
                }
                val result = repository.getSectionContent(sectionId)
                result.fold(
                    onSuccess = { sectionContent ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentSectionContent = sectionContent,
                                navigationStack = it.navigationStack + node,
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
                state.copy(
                    navigationStack = state.navigationStack.dropLast(1),
                    currentSectionContent = null
                )
            } else {
                state
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
        showOperationDialog(subSectionNode)
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
        showOperationDialog(questionNode)
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
                    AdminOperation.ADD_SUBSECTION -> {
                        val subSection = FaqSubSection(
                            id = repository.generateId(),
                            sectionId = node.id,
                            title = formData.title,
                            description = null
                        )
                        repository.addSubSection(subSection)
                    }
                    AdminOperation.ADD_QUESTION -> {
                        val answer: Any = when (formData.answerType) {
                            AnswerType.TEXT -> formData.textAnswer
                            AnswerType.BULLET_POINTS -> formData.bulletPoints
                        }
                        
                        val question = FaqQuestion(
                            id = repository.generateId(),
                            sectionId = node.sectionId ?: node.id,
                            subSectionId = node.subSectionId,
                            question = formData.question,
                            answerType = formData.answerType,
                            answer = answer
                        )
                        repository.addQuestion(question)
                    }
                    AdminOperation.EDIT_QUESTION -> {
                        val existingQuestion = node.question ?: return@launch
                        val answer: Any = when (formData.answerType) {
                            AnswerType.TEXT -> formData.textAnswer
                            AnswerType.BULLET_POINTS -> formData.bulletPoints
                        }
                        
                        val updatedQuestion = existingQuestion.copy(
                            question = formData.question,
                            answerType = formData.answerType,
                            answer = answer
                        )
                        repository.updateQuestion(updatedQuestion)
                    }
                    else -> Result.failure(Exception("Unsupported operation"))
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
        private val userType: String,
        private val isTeachingWing: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AdminFaqViewModel::class.java)) {
                return AdminFaqViewModel(repository, userType, isTeachingWing) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
