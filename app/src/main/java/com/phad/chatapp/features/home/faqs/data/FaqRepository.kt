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
    fun getFaqData(): Flow<Result<Triple<Map<String, FaqSection>, Map<String, List<FaqSubSection>>, Map<String, List<FaqQuestion>>>>> = flow {
        try {
            // Single-collection model: 'faqs' with type = 'section' | 'question'
            val sectionsSnap = firestore.collection("faqs")
                .whereEqualTo("type", "section")
                .get()
                .await()

            // Build raw section map to compute ancestry
            data class RawSection(
                val id: String,
                val title: String,
                val parentId: String?
            )

            val rawSections = sectionsSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val id = doc.id
                val title = data["title"] as? String ?: ""
                val parent = (data["parent_section"] as? String)?.takeIf { it.isNotBlank() }
                RawSection(id, title, parent)
            }
            val idToSection = rawSections.associateBy { it.id }

            fun findRoot(id: String): String? {
                var current: RawSection? = idToSection[id]
                var guard = 0
                while (current != null && current.parentId != null && guard < 100) {
                    current = idToSection[current.parentId]
                    guard++
                }
                return current?.id
            }

            // All root sections (no parent)
            val roots = rawSections.filter { it.parentId == null }
            val sections: Map<String, FaqSection> = roots.associate { rs ->
                rs.id to FaqSection(id = rs.id, title = rs.title)
            }

            // Build subsections list per root - only direct children
            val subSections = mutableMapOf<String, MutableList<FaqSubSection>>()
            rawSections.filter { it.parentId != null }.forEach { rs ->
                val parentId = rs.parentId!! // Safe because we filtered for non-null
                val parent = idToSection[parentId]
                val parentIsRoot = parent?.parentId == null
                
                if (parentIsRoot && sections.containsKey(parentId)) {
                    // This is a direct child of a root section
                    val sub = FaqSubSection(
                        id = rs.id,
                        sectionId = parentId,
                        title = rs.title,
                        description = null,
                        parentSubSectionId = null
                    )
                    val list = subSections.getOrPut(parentId) { mutableListOf() }
                    list.add(sub)
                }
            }

            // Questions
            val questionsSnap = firestore.collection("faqs")
                .whereEqualTo("type", "question")
                .get()
                .await()

            val questions = mutableMapOf<String, MutableList<FaqQuestion>>()
            questionsSnap.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                val id = doc.id
                val parent = (data["parent_section"] as? String)?.takeIf { it.isNotBlank() } ?: return@forEach
                val q = (data["question"] as? String) ?: return@forEach
                val ans = (data["answer"] ?: "").toString()

                val parentSection = idToSection[parent]
                val parentIsRoot = parentSection?.parentId == null
                
                if (parentIsRoot && sections.containsKey(parent)) {
                    // This is a direct question under a root section
                    val fq = FaqQuestion(
                        id = id,
                        sectionId = parent,
                        subSectionId = null,
                        question = q,
                        answerType = AnswerType.TEXT,
                        answer = ans
                    )
                    val list = questions.getOrPut(parent) { mutableListOf() }
                    list.add(fq)
                } else if (parentSection != null) {
                    // This is a question under a subsection
                    val root = findRoot(parent) ?: return@forEach
                    if (sections.containsKey(root)) {
                        val fq = FaqQuestion(
                            id = id,
                            sectionId = root,
                            subSectionId = parent,
                            question = q,
                            answerType = AnswerType.TEXT,
                            answer = ans
                        )
                        val list = questions.getOrPut(root) { mutableListOf() }
                        list.add(fq)
                    }
                }
            }

            emit(Result.success(Triple(sections, subSections.mapValues { it.value }, questions.mapValues { it.value })))
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
            firestore.collection("faqs")
                .document(section.id)
                .set(
                    mapOf(
                        "type" to "section",
                        "title" to section.title,
                        "parent_section" to null
                    )
                ).await()
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
            firestore.collection("faqs")
                .document(section.id)
                .update(
                    mapOf(
                        "title" to section.title
                    )
                ).await()
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
            // Collect all descendant sections (BFS)
            val allSections = firestore.collection("faqs")
                .whereEqualTo("type", "section")
                .get().await().documents
                .associateBy({ it.id }, { it.data ?: emptyMap<String, Any>() })

            fun childrenOf(parent: String): List<String> = allSections.filter { (_, data) ->
                (data["parent_section"] as? String) == parent
            }.keys.toList()

            val toDelete = mutableListOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(sectionId)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                toDelete.add(cur)
                childrenOf(cur).forEach { queue.add(it) }
            }

            // Delete questions whose parent_section is any of toDelete
            val questions = firestore.collection("faqs")
                .whereEqualTo("type", "question")
                .get().await().documents
            questions.forEach { qdoc ->
                val parent = (qdoc.get("parent_section") as? String) ?: return@forEach
                if (toDelete.contains(parent)) {
                    qdoc.reference.delete().await()
                }
            }

            // Delete sections
            toDelete.forEach { sid ->
                firestore.collection("faqs").document(sid).delete().await()
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
            val parentForDoc = subSection.parentSubSectionId ?: subSection.sectionId
            firestore.collection("faqs")
                .document(subSection.id)
                .set(
                    mapOf(
                        "type" to "section",
                        "title" to subSection.title,
                        "parent_section" to parentForDoc
                    )
                ).await()
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
            firestore.collection("faqs")
                .document(subSection.id)
                .update(
                    mapOf(
                        "title" to subSection.title
                    )
                ).await()
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
            // Reuse deleteSection logic since subsections are also 'section' docs now
            deleteSection(subSectionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a new question
     */
    suspend fun addQuestion(question: FaqQuestion): Result<String> {
        return try {
            val parentForDoc = question.subSectionId ?: question.sectionId
            firestore.collection("faqs")
                .document(question.id)
                .set(
                    mapOf(
                        "type" to "question",
                        "parent_section" to parentForDoc,
                        "question" to question.question,
                        "answer_type" to "text",
                        "answer" to (question.answer as? String ?: question.answer.toString())
                    )
                ).await()
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
            firestore.collection("faqs")
                .document(question.id)
                .update(
                    mapOf(
                        "question" to question.question,
                        "answer_type" to "text",
                        "answer" to (question.answer as? String ?: question.answer.toString())
                    )
                ).await()
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
            // Load the section doc
            val sectionDoc = firestore.collection("faqs").document(sectionId).get().await()
            val sdata = sectionDoc.data ?: return Result.failure(Exception("Section not found"))
            val section = FaqSection(
                id = sectionId,
                title = sdata["title"] as? String ?: ""
            )

            // Get all sections to determine hierarchy
            val allSectionsSnap = firestore.collection("faqs")
                .whereEqualTo("type", "section")
                .get()
                .await()
            
            val allSections = allSectionsSnap.documents.associateBy({ it.id }, { it.data ?: emptyMap<String, Any>() })
            
            // Find the root section for this section
            fun findRoot(id: String): String? {
                var currentId = id
                var guard = 0
                while (guard < 100) {
                    val current = allSections[currentId]
                    if (current == null) break
                    val parentId = current["parent_section"] as? String
                    if (parentId == null) break
                    currentId = parentId
                    guard++
                }
                return currentId
            }
            
            val rootSectionId = findRoot(sectionId) ?: sectionId

            // Subsections are sections whose parent_section is this section
            val subSectionsQuery = firestore.collection("faqs")
                .whereEqualTo("type", "section")
                .whereEqualTo("parent_section", sectionId)
                .get().await()
            val subSections = subSectionsQuery.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FaqSubSection(
                    id = doc.id,
                    sectionId = rootSectionId,
                    title = data["title"] as? String ?: return@mapNotNull null,
                    description = null,
                    parentSubSectionId = if (sectionId == rootSectionId) null else sectionId
                )
            }

            val questionsQuery = firestore.collection("faqs")
                .whereEqualTo("type", "question")
                .whereEqualTo("parent_section", sectionId)
                .get().await()
            val questions = questionsQuery.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                FaqQuestion(
                    id = doc.id,
                    sectionId = rootSectionId,
                    subSectionId = if (sectionId == rootSectionId) null else sectionId,
                    question = data["question"] as? String ?: return@mapNotNull null,
                    answerType = AnswerType.TEXT,
                    answer = (data["answer"] ?: "").toString()
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