package com.phad.chatapp.features.home.faqs.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import java.util.UUID

class FaqRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getFaqData(interfaceType: String): Flow<Result<Triple<Map<String, FaqSection>, Map<String, List<FaqSubSection>>, Map<String, List<FaqQuestion>>>>> = flow {
        try {
            // Fetch sections
            val sectionsDoc = firestore.collection("faqs").document("sections").get().await()
            val sectionsData = sectionsDoc.data ?: emptyMap()
            
            val sections = sectionsData.mapValues { (_, value) ->
                @Suppress("UNCHECKED_CAST")
                val sectionData = value as Map<String, Any>
                FaqSection(
                    id = sectionData["id"] as? String ?: "",
                    title = sectionData["title"] as? String ?: "",
                    interfaceTypes = (sectionData["interface_types"] as? List<*>)?.map { it.toString() } ?: emptyList()
                )
            }.filter { (_, section) ->
                section.interfaceTypes.contains(interfaceType)
            }

            // Fetch sub-sections
            val subSections = mutableMapOf<String, List<FaqSubSection>>()
            sections.keys.forEach { sectionId ->
                val sectionSubSections = firestore.collection("faqs")
                    .document("sub_sections")
                    .collection(sectionId)
                    .get()
                    .await()

                subSections[sectionId] = sectionSubSections.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    FaqSubSection(
                        id = data["id"] as? String ?: return@mapNotNull null,
                        sectionId = data["section_id"] as? String ?: return@mapNotNull null,
                        title = data["title"] as? String ?: return@mapNotNull null,
                        description = data["description"] as? String
                    )
                }
            }

            // Fetch questions for each section and sub-section
            val questions = mutableMapOf<String, List<FaqQuestion>>()
            sections.keys.forEach { sectionId ->
                val sectionQuestions = firestore.collection("faqs")
                    .document("questions")
                    .collection(sectionId)
                    .get()
                    .await()

                questions[sectionId] = sectionQuestions.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    FaqQuestion(
                        id = data["id"] as? String ?: return@mapNotNull null,
                        sectionId = data["section_id"] as? String ?: return@mapNotNull null,
                        subSectionId = data["sub_section_id"] as? String,
                        question = data["question"] as? String ?: return@mapNotNull null,
                        answerType = when (data["answer_type"] as? String) {
                            "text" -> AnswerType.TEXT
                            "bullet_points" -> AnswerType.BULLET_POINTS
                            else -> AnswerType.TEXT
                        },
                        answer = data["answer"] ?: ""
                    )
                }
            }

            emit(Result.success(Triple(sections, subSections, questions)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.catch { e ->
        emit(Result.failure(e))
    }

    // Admin functions for CRUD operations

    /**
     * Add a new section
     */
    suspend fun addSection(section: FaqSection): Result<String> {
        return try {
            val sectionsDoc = firestore.collection("faqs").document("sections").get().await()
            val currentSections = sectionsDoc.data?.toMutableMap() ?: mutableMapOf()

            currentSections[section.id] = mapOf(
                "id" to section.id,
                "title" to section.title,
                "interface_types" to section.interfaceTypes
            )

            firestore.collection("faqs").document("sections").set(currentSections).await()
            Result.success(section.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing section
     */
    suspend fun updateSection(section: FaqSection): Result<Unit> {
        return try {
            val sectionsDoc = firestore.collection("faqs").document("sections").get().await()
            val currentSections = sectionsDoc.data?.toMutableMap() ?: mutableMapOf()

            currentSections[section.id] = mapOf(
                "id" to section.id,
                "title" to section.title,
                "interface_types" to section.interfaceTypes
            )

            firestore.collection("faqs").document("sections").set(currentSections).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a section and all its subsections and questions
     */
    suspend fun deleteSection(sectionId: String): Result<Unit> {
        return try {
            // Delete from sections document
            val sectionsDoc = firestore.collection("faqs").document("sections").get().await()
            val currentSections = sectionsDoc.data?.toMutableMap() ?: mutableMapOf()
            currentSections.remove(sectionId)
            firestore.collection("faqs").document("sections").set(currentSections).await()

            // Delete all subsections for this section
            val subSectionsQuery = firestore.collection("faqs")
                .document("sub_sections")
                .collection(sectionId)
                .get()
                .await()

            subSectionsQuery.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            // Delete all questions for this section
            val questionsQuery = firestore.collection("faqs")
                .document("questions")
                .collection(sectionId)
                .get()
                .await()

            questionsQuery.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a new subsection
     */
    suspend fun addSubSection(subSection: FaqSubSection): Result<String> {
        return try {
            val subSectionData = mapOf(
                "id" to subSection.id,
                "section_id" to subSection.sectionId,
                "title" to subSection.title,
                "description" to (subSection.description ?: "")
            )

            firestore.collection("faqs")
                .document("sub_sections")
                .collection(subSection.sectionId)
                .document(subSection.id)
                .set(subSectionData)
                .await()

            Result.success(subSection.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing subsection
     */
    suspend fun updateSubSection(subSection: FaqSubSection): Result<Unit> {
        return try {
            val subSectionData = mapOf(
                "id" to subSection.id,
                "section_id" to subSection.sectionId,
                "title" to subSection.title,
                "description" to (subSection.description ?: "")
            )

            firestore.collection("faqs")
                .document("sub_sections")
                .collection(subSection.sectionId)
                .document(subSection.id)
                .set(subSectionData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a subsection and all its questions
     */
    suspend fun deleteSubSection(sectionId: String, subSectionId: String): Result<Unit> {
        return try {
            // Delete the subsection
            firestore.collection("faqs")
                .document("sub_sections")
                .collection(sectionId)
                .document(subSectionId)
                .delete()
                .await()

            // Delete all questions for this subsection
            val questionsQuery = firestore.collection("faqs")
                .document("questions")
                .collection(sectionId)
                .whereEqualTo("sub_section_id", subSectionId)
                .get()
                .await()

            questionsQuery.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a new question
     */
    suspend fun addQuestion(question: FaqQuestion): Result<String> {
        return try {
            val questionData = mapOf(
                "id" to question.id,
                "section_id" to question.sectionId,
                "sub_section_id" to question.subSectionId,
                "question" to question.question,
                "answer_type" to when (question.answerType) {
                    AnswerType.TEXT -> "text"
                    AnswerType.BULLET_POINTS -> "bullet_points"
                },
                "answer" to question.answer
            )

            firestore.collection("faqs")
                .document("questions")
                .collection(question.sectionId)
                .document(question.id)
                .set(questionData)
                .await()

            Result.success(question.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing question
     */
    suspend fun updateQuestion(question: FaqQuestion): Result<Unit> {
        return try {
            val questionData = mapOf(
                "id" to question.id,
                "section_id" to question.sectionId,
                "sub_section_id" to question.subSectionId,
                "question" to question.question,
                "answer_type" to when (question.answerType) {
                    AnswerType.TEXT -> "text"
                    AnswerType.BULLET_POINTS -> "bullet_points"
                },
                "answer" to question.answer
            )

            firestore.collection("faqs")
                .document("questions")
                .collection(question.sectionId)
                .document(question.id)
                .set(questionData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a question
     */
    suspend fun deleteQuestion(sectionId: String, questionId: String): Result<Unit> {
        return try {
            firestore.collection("faqs")
                .document("questions")
                .collection(sectionId)
                .document(questionId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get section content (subsections and questions) for admin browsing
     */
    suspend fun getSectionContent(sectionId: String): Result<SectionContent> {
        return try {
            // Get section info
            val sectionsDoc = firestore.collection("faqs").document("sections").get().await()
            val sectionsData = sectionsDoc.data ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val sectionData = sectionsData[sectionId] as? Map<String, Any>

            val section = if (sectionData != null) {
                FaqSection(
                    id = sectionData["id"] as? String ?: sectionId,
                    title = sectionData["title"] as? String ?: "",
                    interfaceTypes = (sectionData["interface_types"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            } else {
                return Result.failure(Exception("Section not found"))
            }

            // Get subsections
            val subSectionsQuery = firestore.collection("faqs")
                .document("sub_sections")
                .collection(sectionId)
                .get()
                .await()

            val subSections = subSectionsQuery.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FaqSubSection(
                    id = data["id"] as? String ?: return@mapNotNull null,
                    sectionId = data["section_id"] as? String ?: return@mapNotNull null,
                    title = data["title"] as? String ?: return@mapNotNull null,
                    description = data["description"] as? String
                )
            }

            // Get questions
            val questionsQuery = firestore.collection("faqs")
                .document("questions")
                .collection(sectionId)
                .get()
                .await()

            val questions = questionsQuery.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FaqQuestion(
                    id = data["id"] as? String ?: return@mapNotNull null,
                    sectionId = data["section_id"] as? String ?: return@mapNotNull null,
                    subSectionId = data["sub_section_id"] as? String,
                    question = data["question"] as? String ?: return@mapNotNull null,
                    answerType = when (data["answer_type"] as? String) {
                        "text" -> AnswerType.TEXT
                        "bullet_points" -> AnswerType.BULLET_POINTS
                        else -> AnswerType.TEXT
                    },
                    answer = data["answer"] ?: ""
                )
            }

            Result.success(SectionContent(section, subSections, questions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a unique ID for new FAQ items
     */
    fun generateId(): String {
        return UUID.randomUUID().toString()
    }
}