package com.phad.chatapp.features.home.chatbot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phad.chatapp.features.home.chatbot.data.ChatMessage
import com.phad.chatapp.features.home.chatbot.data.FirebaseRepository
import com.phad.chatapp.features.home.chatbot.data.QuestionNode
import kotlinx.coroutines.launch

// Custom colors from palette
val Asphalt = Color(0xFF0B0904)
val Supernova = Color(0xFFFBCB04)
val YukonGold = Color(0xFF745C04)
val HotToddy = Color(0xFFA38304)
val Corn = Color(0xFFDCAC04)
val BuddhaGold = Color(0xFFC79F04)

class ChatBotActivity : ComponentActivity() {
    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatBotTheme {
                ChatBotScreen(
                    onNavigateBack = { finish() },
                    repository = repository
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBotScreen(
    onNavigateBack: () -> Unit,
    repository: FirebaseRepository,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages = uiState.messages
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChatBot") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chat messages area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Bottom section with typing box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Asphalt)
                    .padding(16.dp)
            ) {
                // Typing box with send button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("Type your message...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = HotToddy,
                            unfocusedBorderColor = YukonGold
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                viewModel.sendMessage(userInput)
                                userInput = ""
                            }
                        },
                        modifier = Modifier
                            .background(
                                color = Supernova,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Asphalt
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isFromUser) Supernova else HotToddy
    val textColor = if (message.isFromUser) Asphalt else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp),
            color = color,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 0.dp,
                bottomEnd = if (message.isFromUser) 0.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    modifier = Modifier.padding(4.dp)
                )
                
                // Show timestamp (optional)
                if (!message.isGenerating) {
                    Text(
                        text = message.timestamp.toLocalTime().toString(),
                        fontSize = 10.sp,
                        color = if (message.isFromUser) Asphalt.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun searchAnswer(searchText: String, nodes: Map<String, QuestionNode>?): String? {
    if (nodes == null) return null
    
    // Simple search function that looks for keywords in questions and answers
    for ((_, node) in nodes) {
        if (node.text.contains(searchText, ignoreCase = true)) {
            return node.answer ?: searchAnswer(searchText, node.children)
        }
        
        if (node.answer?.contains(searchText, ignoreCase = true) == true) {
            return node.answer
        }
        
        val childResult = searchAnswer(searchText, node.children)
        if (childResult != null) {
            return childResult
        }
    }
    
    return null
}

@Composable
fun QuestionTreeButtons(
    nodes: Map<String, QuestionNode>,
    onNodeClick: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        for ((id, node) in nodes) {
            Button(
                onClick = { onNodeClick(id, node.text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BuddhaGold
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = node.text,
                    color = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Supernova,
            secondary = HotToddy,
            background = Color.White,
            surface = Color.White
        ),
        content = content
    )
} 