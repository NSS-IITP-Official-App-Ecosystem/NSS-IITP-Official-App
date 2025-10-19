package com.phad.chatapp.features.home.faqs.data

data class FaqSection(
    val id: String,
    val title: String
)

data class FaqSubSection(
    val id: String,
    val sectionId: String,
    val title: String,
    val description: String? = null,
    val parentSubSectionId: String? = null // For nested subsections
)

data class FaqQuestion(
    val id: String,
    val sectionId: String,
    val subSectionId: String? = null,
    val question: String,
    val answerType: AnswerType,
    val answer: Any // String only (use \\bpt(...) syntax for bullets)
)

enum class AnswerType {
    TEXT
}

// Removed Wings format

sealed class FaqNavigationItem {
    data class Section(val section: FaqSection) : FaqNavigationItem()
    data class SubSection(val subSection: FaqSubSection) : FaqNavigationItem()
    data class Question(val question: FaqQuestion) : FaqNavigationItem()
}

data class FaqSearchResult(
    val question: FaqQuestion,
    val sectionTitle: String,
    val subSectionTitle: String?
)

data class FaqUiState(
    val sections: Map<String, FaqSection> = emptyMap(),
    val subSections: Map<String, List<FaqSubSection>> = emptyMap(),
    val questions: Map<String, List<FaqQuestion>> = emptyMap(),
    val loadedSubSectionContent: Map<String, SectionContent> = emptyMap(), // Store loaded subsection content
    val navigationStack: List<FaqNavigationItem> = emptyList(),
    val history: List<FaqQuestion> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<FaqSearchResult> = emptyList(),
    val isSearchMode: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) 