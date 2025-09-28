package com.phad.chatapp.features.home.faqs.data

/**
 * Data models for admin FAQ operations
 */

// Admin operation types
enum class AdminOperation {
    EDIT_SECTION_NAME,
    OPEN_SECTION,
    ADD_SUBSECTION,
    ADD_QUESTION,
    DELETE_SECTION,
    EDIT_QUESTION,
    DELETE_QUESTION
}

// Node types in the FAQ tree
enum class FaqNodeType {
    ROOT_SECTION,
    SUBSECTION,
    QUESTION
}

// Admin UI state for the FAQ management interface
data class AdminFaqUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserType: String = "",
    val isTeachingWing: Boolean = false,
    val rootSections: List<FaqSection> = emptyList(),
    val showOperationDialog: Boolean = false,
    val selectedNode: FaqNode? = null,
    val operationType: AdminOperation? = null,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val navigationStack: List<FaqNode> = emptyList(),
    val currentSectionContent: SectionContent? = null
)

// Represents a node in the FAQ tree for admin operations
data class FaqNode(
    val id: String,
    val title: String,
    val type: FaqNodeType,
    val sectionId: String? = null,
    val subSectionId: String? = null,
    val question: FaqQuestion? = null,
    val section: FaqSection? = null,
    val subSection: FaqSubSection? = null
)

// Request models for admin operations
data class AddSectionRequest(
    val title: String,
    val interfaceTypes: List<String>
)

data class AddSubSectionRequest(
    val sectionId: String,
    val title: String,
    val description: String? = null
)

data class AddQuestionRequest(
    val sectionId: String,
    val subSectionId: String? = null,
    val question: String,
    val answerType: AnswerType,
    val answer: Any
)

data class UpdateSectionRequest(
    val id: String,
    val title: String,
    val interfaceTypes: List<String>
)

data class UpdateSubSectionRequest(
    val id: String,
    val sectionId: String,
    val title: String,
    val description: String? = null
)

data class UpdateQuestionRequest(
    val id: String,
    val sectionId: String,
    val subSectionId: String? = null,
    val question: String,
    val answerType: AnswerType,
    val answer: Any
)

// Dialog state for different admin operations
data class AdminDialogState(
    val isVisible: Boolean = false,
    val title: String = "",
    val operation: AdminOperation? = null,
    val node: FaqNode? = null,
    val formData: AdminFormData = AdminFormData()
)

// Form data for admin dialogs
data class AdminFormData(
    val title: String = "",
    val description: String = "",
    val question: String = "",
    val answerType: AnswerType = AnswerType.TEXT,
    val textAnswer: String = "",
    val bulletPoints: List<String> = emptyList()
)

// Available operations for different node types
object AdminOperations {
    fun getOperationsForNode(nodeType: FaqNodeType): List<AdminOperation> {
        return when (nodeType) {
            FaqNodeType.ROOT_SECTION -> listOf(
                AdminOperation.EDIT_SECTION_NAME,
                AdminOperation.OPEN_SECTION,
                AdminOperation.ADD_SUBSECTION,
                AdminOperation.ADD_QUESTION
            )
            FaqNodeType.SUBSECTION -> listOf(
                AdminOperation.EDIT_SECTION_NAME,
                AdminOperation.OPEN_SECTION,
                AdminOperation.ADD_SUBSECTION,
                AdminOperation.ADD_QUESTION,
                AdminOperation.DELETE_SECTION
            )
            FaqNodeType.QUESTION -> listOf(
                AdminOperation.EDIT_QUESTION,
                AdminOperation.DELETE_QUESTION
            )
        }
    }
    
    fun getOperationDisplayName(operation: AdminOperation): String {
        return when (operation) {
            AdminOperation.EDIT_SECTION_NAME -> "Edit Section Name"
            AdminOperation.OPEN_SECTION -> "Open Section"
            AdminOperation.ADD_SUBSECTION -> "Add Sub-section"
            AdminOperation.ADD_QUESTION -> "Add Question"
            AdminOperation.DELETE_SECTION -> "Delete Section"
            AdminOperation.EDIT_QUESTION -> "Edit Question"
            AdminOperation.DELETE_QUESTION -> "Delete Question"
        }
    }
}

// Section content for browsing
data class SectionContent(
    val section: FaqSection,
    val subSections: List<FaqSubSection> = emptyList(),
    val questions: List<FaqQuestion> = emptyList()
)

// Interface types for different user categories
object InterfaceTypes {
    const val NON_TEACHING = "non_teaching"
    const val TEACHING_WING = "teaching_wing"

    fun getRootSectionsForUserType(isTeachingWing: Boolean): List<String> {
        return if (isTeachingWing) {
            listOf("general_questions", "nss_hierarchy")
        } else {
            listOf("general_questions", "teaching_technical_wing", "nss_hierarchy")
        }
    }

    fun getInterfaceTypesForUserType(isTeachingWing: Boolean): List<String> {
        return if (isTeachingWing) {
            listOf(TEACHING_WING)
        } else {
            listOf(NON_TEACHING)
        }
    }
}
