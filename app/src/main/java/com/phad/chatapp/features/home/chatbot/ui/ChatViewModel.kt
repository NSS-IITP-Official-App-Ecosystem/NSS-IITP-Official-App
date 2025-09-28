package com.phad.chatapp.features.home.chatbot.ui

import androidx.lifecycle.ViewModel
import com.phad.chatapp.features.home.chatbot.data.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val questionResponses = mapOf(
        // Attendance Related
        "attendance" to "Your attendance is tracked for each class and event. You need to maintain at least 75% attendance in all classes to complete the teaching wing requirements.\n\nWould you like to:\n📅 Check attendance for a specific date?\n📊 Download attendance report?\n📈 View attendance statistics?",
        "specific_date" to "Yes, you can check your attendance for any specific date:\n\n1. Go to the 'Attendance' section\n2. Use the calendar view to select your date\n3. View detailed attendance status\n4. See teacher remarks if any\n\nYou can also filter by:\n- Subject wise attendance\n- Monthly attendance\n- Event attendance",
        "attendance_report" to "Yes, you can download your attendance report in multiple formats:\n\n1. Go to 'Reports' section\n2. Select 'Attendance Report'\n3. Choose your preferred format:\n   📑 PDF Format\n   📊 Excel Format\n   📱 Mobile-friendly Format\n\nYou can also:\n- Set custom date ranges\n- Include teacher remarks\n- Add event attendance",
        "attendance_low" to "If your attendance is below 75%:\n\n1. You'll receive a notification\n2. Your mentor will be informed\n3. You can request for:\n   - Make-up classes\n   - Special consideration\n   - Additional assignments",

        // Timetable Related
        "timetable" to "Your timetable shows all scheduled activities:\n\n📅 View Options:\n- Daily schedule\n- Weekly planner\n- Monthly calendar\n\n🎯 Features:\n- Class timings\n- Teaching assignments\n- Events & workshops\n- Mentor meetings\n\nWould you like to:\n🔄 Sync with your calendar?\n⏰ Set reminders?\n📱 View on mobile?",
        "timetable_sync" to "Sync your timetable with your preferred calendar:\n\n1. Go to Settings > Calendar Sync\n2. Choose your platform:\n   📱 Google Calendar\n   🍎 Apple Calendar\n   📅 Outlook\n\nFeatures:\n- Automatic updates\n- Custom notifications\n- Offline access",
        "timetable_clash" to "In case of schedule clashes:\n\n1. System will notify you\n2. Alternative slots will be suggested\n3. You can:\n   - Request slot change\n   - Contact coordinator\n   - Arrange make-up class",

        // Course Materials
        "course_materials" to "Access course materials in the 'Resources' section:\n\n📚 Available Materials:\n- Lecture notes\n- Presentations\n- Reference books\n- Practice materials\n- Video lectures\n\n💡 Features:\n- Search by topic\n- Filter by subject\n- Sort by date\n- Mark favorites\n\nWould you like to:\n⬇️ Download for offline use?\n🔍 Search specific topic?\n📑 View recent uploads?",
        "download_materials" to "Download materials for offline access:\n\n1. Look for the ⬇️ icon\n2. Choose download options:\n   📱 Mobile-optimized\n   💻 Full resolution\n   📄 Print-ready\n\nFeatures:\n- Bulk download\n- Auto-sync\n- Storage management",
        "material_request" to "Need specific materials?\n\n1. Use 'Request Material' feature\n2. Specify:\n   - Subject/Topic\n   - Format needed\n   - Urgency level\n3. Track request status",

        // Notifications
        "notifications" to "Stay updated with important notifications:\n\n🔔 You'll receive alerts for:\n- Class schedule changes\n- New assignments\n- Important announcements\n- Mentor feedback\n- Attendance updates\n- Event reminders\n\nWould you like to:\n⚙️ Customize notification settings?\n📱 Set up mobile alerts?\n🔕 Manage quiet hours?",
        "notification_settings" to "Customize your notifications in Settings:\n\n⚙️ Configure:\n1. Notification types:\n   - Push notifications\n   - Email alerts\n   - SMS updates\n\n2. Priority levels:\n   - High priority\n   - Normal updates\n   - Silent notifications\n\n3. Quiet hours:\n   - Set time range\n   - Choose days\n   - Exception rules",

        // Teaching Requirements
        "teaching_requirements" to "To complete teaching wing requirements:\n\n1. Core Requirements:\n   ✓ Attend all assigned classes\n   ✓ Participate in teaching events\n   ✓ Complete teaching duties\n   ✓ Maintain 75% attendance\n\n2. Additional Tasks:\n   ✓ Prepare lesson plans\n   ✓ Create teaching materials\n   ✓ Attend workshops\n   ✓ Submit reports\n\n3. Evaluation Criteria:\n   ✓ Teaching performance\n   ✓ Student feedback\n   ✓ Professional development\n\nWould you like details about:\n📊 Evaluation process?\n👥 Mentorship program?\n📝 Documentation needed?",

        // Evaluation Process
        "evaluation" to "Teaching evaluation includes:\n\n1. Classroom Performance (40%):\n   ✓ Teaching methodology\n   ✓ Student engagement\n   ✓ Content delivery\n   ✓ Time management\n\n2. Student Feedback (25%):\n   ✓ Teaching effectiveness\n   ✓ Communication skills\n   ✓ Helpfulness\n\n3. Mentor Assessment (25%):\n   ✓ Professional growth\n   ✓ Teaching skills\n   ✓ Initiative\n\n4. Participation (10%):\n   ✓ Events attendance\n   ✓ Workshop participation\n   ✓ Team collaboration",

        // Mentorship
        "mentorship" to "Your mentorship program includes:\n\n👥 Mentor Support:\n- Regular meetings\n- Performance review\n- Career guidance\n- Teaching tips\n\n📈 Development Areas:\n- Teaching skills\n- Classroom management\n- Student engagement\n- Professional growth\n\n🎯 Activities:\n- Observation classes\n- Feedback sessions\n- Skill workshops\n- Peer learning\n\nWould you like to:\n📅 Schedule mentor meeting?\n📋 View feedback?\n📝 Set development goals?",

        // Feedback System
        "feedback" to "Access your feedback in your profile:\n\n📊 Available Feedback:\n1. Student Feedback:\n   - Class ratings\n   - Comments\n   - Suggestions\n\n2. Mentor Feedback:\n   - Performance review\n   - Areas of improvement\n   - Recommendations\n\n3. Peer Feedback:\n   - Collaboration\n   - Teaching style\n   - Best practices\n\nWould you like to:\n📈 View feedback history?\n📝 Response to feedback?\n🎯 Create improvement plan?",

        // Certificates
        "certificates" to "Earn your teaching certificates:\n\n🎓 Requirements:\n1. Complete teaching hours\n2. Pass evaluations\n3. Submit documentation\n4. Attend workshops\n\n📜 Certificate Types:\n- Teaching Excellence\n- Specialist Certifications\n- Workshop Completion\n- Special Achievement\n\n⭐ Benefits:\n- Professional recognition\n- Career advancement\n- Skill validation\n\nWould you like to:\n📋 Check eligibility?\n📊 Track progress?\n🏆 View available certificates?",

        // Help & Support
        "help" to "Need assistance? Here's how to get help:\n\n1. Direct Support:\n   👥 Contact mentor\n   📧 Email: support@eduverse.com\n   📞 Helpline: 1800-EDU-HELP\n\n2. Self-Help Resources:\n   📚 Knowledge base\n   ❓ FAQ section\n   📝 User guides\n   🎥 Tutorial videos\n\n3. Community Support:\n   👥 Discussion forums\n   💭 Peer support\n   👨‍🏫 Expert connect\n\n4. Technical Support:\n   🔧 System issues\n   💻 App support\n   🔑 Account help",

        // Default Response
        "default" to "I'm here to help! Here are our main topics:\n\n📚 Teaching Wing:\n- Requirements\n- Evaluation\n- Mentorship\n- Certificates\n\n📊 Daily Tasks:\n- Attendance\n- Timetable\n- Course Materials\n- Notifications\n\n🤝 Support:\n- Help Center\n- Feedback\n- Technical Support\n\nPlease ask about any of these topics!"
    )

    init {
        _uiState.value = ChatUiState(
            messages = listOf(
                ChatMessage(
                    text = "👋 Welcome to EduVerse Assistant!\n\nI'm here to help you with all your teaching wing queries. Here are some common topics:",
                    isFromUser = false,
                    timestamp = LocalDateTime.now()
                ),
                ChatMessage(
                    text = "📚 Teaching Wing:\n" +
                           "- How can I check my attendance?\n" +
                           "- What are the teaching requirements?\n" +
                           "- How is teaching evaluated?\n" +
                           "- Tell me about mentorship\n\n" +
                           "📊 Daily Activities:\n" +
                           "- How do I view my timetable?\n" +
                           "- Where can I access course materials?\n" +
                           "- How do I manage notifications?\n\n" +
                           "🎓 Progress & Support:\n" +
                           "- Where can I view feedback?\n" +
                           "- How do I get certificates?\n" +
                           "- Need help with something else?\n\n" +
                           "Please choose a topic or ask your question! 😊",
                    isFromUser = false,
                    timestamp = LocalDateTime.now()
                )
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val currentState = _uiState.value
        val userMessage = ChatMessage(
            text = text,
            isFromUser = true,
            timestamp = LocalDateTime.now()
        )
        
        val botGeneratingMessage = ChatMessage(
            text = "Generating...",
            isFromUser = false,
            timestamp = LocalDateTime.now(),
            isGenerating = true
        )

        _uiState.value = currentState.copy(
            messages = currentState.messages + userMessage + botGeneratingMessage,
            inputText = ""
        )

        // Determine response based on keywords
        val response = when {
            // Attendance Queries
            text.contains("attendance", ignoreCase = true) && text.contains("specific date", ignoreCase = true) -> 
                questionResponses["specific_date"]
            text.contains("attendance", ignoreCase = true) && text.contains("report", ignoreCase = true) -> 
                questionResponses["attendance_report"]
            text.contains("attendance", ignoreCase = true) && (text.contains("low", ignoreCase = true) || text.contains("below", ignoreCase = true)) ->
                questionResponses["attendance_low"]
            text.contains("attendance", ignoreCase = true) -> 
                questionResponses["attendance"]

            // Timetable Queries
            text.contains("timetable", ignoreCase = true) && text.contains("sync", ignoreCase = true) -> 
                questionResponses["timetable_sync"]
            text.contains("timetable", ignoreCase = true) && text.contains("clash", ignoreCase = true) ->
                questionResponses["timetable_clash"]
            text.contains("timetable", ignoreCase = true) -> 
                questionResponses["timetable"]

            // Course Materials Queries
            text.contains("course", ignoreCase = true) && text.contains("download", ignoreCase = true) -> 
                questionResponses["download_materials"]
            text.contains("material", ignoreCase = true) && text.contains("request", ignoreCase = true) ->
                questionResponses["material_request"]
            text.contains("course", ignoreCase = true) || text.contains("materials", ignoreCase = true) -> 
                questionResponses["course_materials"]

            // Notification Queries
            text.contains("notification", ignoreCase = true) && text.contains("settings", ignoreCase = true) -> 
                questionResponses["notification_settings"]
            text.contains("notification", ignoreCase = true) -> 
                questionResponses["notifications"]

            // Teaching Requirements and Evaluation
            text.contains("teaching", ignoreCase = true) && text.contains("requirements", ignoreCase = true) -> 
                questionResponses["teaching_requirements"]
            text.contains("evaluation", ignoreCase = true) || text.contains("evaluated", ignoreCase = true) -> 
                questionResponses["evaluation"]

            // Mentorship and Feedback
            text.contains("mentor", ignoreCase = true) -> 
                questionResponses["mentorship"]
            text.contains("feedback", ignoreCase = true) -> 
                questionResponses["feedback"]

            // Certificates and Help
            text.contains("certificate", ignoreCase = true) -> 
                questionResponses["certificates"]
            text.contains("help", ignoreCase = true) || text.contains("support", ignoreCase = true) -> 
                questionResponses["help"]

            // Default Response
            else -> questionResponses["default"]
        }

        val botResponse = ChatMessage(
            text = response ?: "I apologize, but I'm not sure how to help with that specific query.",
            isFromUser = false,
            timestamp = LocalDateTime.now()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.dropLast(1) + botResponse
        )
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = ""
) 