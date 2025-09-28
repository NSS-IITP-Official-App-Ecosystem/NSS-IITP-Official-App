package com.phad.chatapp.features.home.chatbot.data

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val database = FirebaseDatabase.getInstance()
    private val questionsRef = database.getReference("chatbot_qna")

    private val dummyData = mapOf(
        "academic" to QuestionNode(
            id = "academic",
            text = "Academic Queries",
            children = mapOf(
                "attendance" to QuestionNode(
                    id = "attendance",
                    text = "Attendance Related",
                    children = mapOf(
                        "check_attendance" to QuestionNode(
                            id = "check_attendance",
                            text = "How to check attendance?",
                            answer = "You can check your attendance in the Student Portal under the 'Attendance' section.",
                            isLeaf = true
                        ),
                        "attendance_shortage" to QuestionNode(
                            id = "attendance_shortage",
                            text = "What if I have attendance shortage?",
                            answer = "Please contact your class teacher if your attendance is below 75%.",
                            isLeaf = true
                        )
                    )
                )
            )
        ),
        "technical" to QuestionNode(
            id = "technical",
            text = "Technical Support",
            children = mapOf(
                "login_issues" to QuestionNode(
                    id = "login_issues",
                    text = "Login Issues",
                    answer = "If you're having trouble logging in, try clearing your cache or resetting your password.",
                    isLeaf = true
                )
            )
        ),
        "general" to QuestionNode(
            id = "general",
            text = "General Information",
            children = mapOf(
                "contact" to QuestionNode(
                    id = "contact",
                    text = "How to contact administration?",
                    answer = "You can reach the administration office at admin@college.edu or visit during office hours (9 AM - 5 PM).",
                    isLeaf = true
                )
            )
        )
    )

    suspend fun getQuestions(): Result<Map<String, QuestionNode>> {
        return try {
            val snapshot = questionsRef.get().await()
            val questions = snapshot.getValue(object : com.google.firebase.database.GenericTypeIndicator<Map<String, QuestionNode>>() {})
            if (questions != null && questions.isNotEmpty()) {
                Result.Success(questions)
            } else {
                // If no data exists, initialize with dummy data
                try {
                    questionsRef.setValue(dummyData).await()
                    Result.Success(dummyData)
                } catch (e: Exception) {
                    Result.Error("Failed to initialize data: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch questions: ${e.message}")
        }
    }

    suspend fun initializeDummyData(): Result<Unit> {
        return try {
            questionsRef.setValue(dummyData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to initialize data: ${e.message}")
        }
    }
} 