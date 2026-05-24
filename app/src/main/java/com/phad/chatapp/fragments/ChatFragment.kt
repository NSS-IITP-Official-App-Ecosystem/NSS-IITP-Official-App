package com.phad.chatapp.fragments

import com.phad.chatapp.activities.GroupChatActivity

import com.phad.chatapp.activities.ChatActivity

import com.phad.chatapp.fragments.ProfileFragment
import android.app.Dialog
import com.phad.chatapp.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.phad.chatapp.adapters.UnreadMessageAdapter
import com.phad.chatapp.adapters.ChatSearchAdapter
import com.phad.chatapp.models.AdminForChat
import com.phad.chatapp.models.Group
import com.phad.chatapp.models.User
import com.phad.chatapp.models.ChatSearchResult
import com.phad.chatapp.repositories.GroupRepository
import com.phad.chatapp.repositories.UnreadMessageRepository
import com.phad.chatapp.services.SubjectAssignmentService
import com.phad.chatapp.ui.chats.ChatScreen
import com.phad.chatapp.ui.chats.ChatUiState
import com.phad.chatapp.utils.SessionManager
import com.phad.chatapp.utils.SubjectSyncUtil
import com.phad.chatapp.utils.AttendanceStatsUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ChatFragment : Fragment() {

    private val TAG = "ChatFragment"
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager
    private lateinit var groupRepository: GroupRepository
    private lateinit var subjectAssignmentService: SubjectAssignmentService
    private var userType: String = ""
    private var userRollNumber: String = ""

    private val _uiState = MutableStateFlow(ChatUiState())
    private val uiState = _uiState.asStateFlow()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state by uiState.collectAsState()
                ChatScreen(
                    state = state,
                    onCommunityClick = { group ->
                        // Optimistically clear the unread indicator for this group
                        _uiState.update { it.copy(unreadGroupChatIds = it.unreadGroupChatIds - group.groupId) }
                        
                        val intent = Intent(requireContext(), GroupChatActivity::class.java)
                        intent.putExtra("GROUP_ID", group.groupId)
                        intent.putExtra("GROUP_NAME", group.name)
                        startActivity(intent)
                    },
                    onUserClick = { user ->
                        // Optimistically clear the unread indicator for this user
                        _uiState.update { it.copy(unreadDirectChatIds = it.unreadDirectChatIds - user.id) }
                        
                        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                            putExtra("currentUserRollNumber", sessionManager.fetchRollNumber())
                            putExtra("currentUserName", sessionManager.fetchUserName())
                            putExtra("otherUserRollNumber", user.id)
                            putExtra("otherUserName", user.name)
                        }
                        startActivity(intent)
                    },
                    onSearchClick = {
                        showSearchDialog()
                    },
                    onRefreshClick = {
                        loadRecentUsers()
                        loadCommunities()
                        fetchUnreadIndicators()
                        if (userType.equals("Admin", ignoreCase = true)) {
                            CoroutineScope(Dispatchers.IO).launch {
                                AttendanceStatsUpdater.updateAttendanceStatsInSession(requireContext())
                            }
                        }
                    },
                    onCreateGroupClick = {
                        findNavController().navigate(R.id.addGroupFragment)
                    },
                    onRemoveGroupClick = {
                        findNavController().navigate(R.id.removeGroupFragment)
                    },
                    onUnreadMessagesClick = {
                        showUnreadMessagesPopup()
                    },
                    onSyncSubjectGroupsClick = {
                        SubjectSyncUtil.syncSubjectAssignments(requireContext()) { success ->
                            if (success) {
                                loadCommunities()
                            }
                        }
                    },
                    currentUserRollNumber = userRollNumber
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        groupRepository = GroupRepository()
        subjectAssignmentService = SubjectAssignmentService()
        userType = sessionManager.fetchUserType() ?: ""
        userRollNumber = sessionManager.fetchRollNumber() ?: ""

        _uiState.update { it.copy(isUserAdmin = userType.equals("Admin", ignoreCase = true)) }

        // Instead, just load data:
        loadRecentUsers()
        loadCommunities()
    }

    override fun onResume() {
        super.onResume()
        fetchUnreadIndicators()
    }

    private fun fetchUnreadIndicators() {
        lifecycleScope.launch {
            try {
                val repo = UnreadMessageRepository(requireContext())
                val (directMessages, groupMessages) = repo.fetchAllUnreadMessages()
                
                val unreadDirectIds = directMessages.map { it.chatPartnerId }.toSet()
                val unreadGroupIds = groupMessages.map { it.groupId }.toSet()
                
                _uiState.update { it.copy(
                    unreadDirectChatIds = unreadDirectIds,
                    unreadGroupChatIds = unreadGroupIds
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching unread indicators", e)
            }
        }
    }

    private fun loadRecentUsers() {
        _uiState.update { it.copy(isLoading = true) }
        lifecycleScope.launch {
            try {
                // 1. Get all active conversations for the current user
                val metadataSnapshot = db.collection("conversation_metadata")
                    .whereArrayContains("participants", userRollNumber)
                    .get().await()
                
                // 2. Sort locally (to avoid needing a Firestore composite index) and extract roll numbers
                val otherUserRollNumbers = metadataSnapshot.documents
                    .sortedByDescending { it.getTimestamp("lastMessageTime") }
                    .mapNotNull { doc ->
                        val participants = doc.get("participants") as? List<*>
                        participants?.firstOrNull { it != userRollNumber }?.toString()
                    }.distinct()
                
                val users = mutableListOf<User>()
                
                // 3. Fetch user details using chunking to avoid N+1 queries (Firestore limits 'in' to 10)
                if (otherUserRollNumbers.isNotEmpty()) {
                    val chunks = otherUserRollNumbers.chunked(10)
                    for (chunk in chunks) {
                        try {
                            val usersSnapshot = db.collection("users")
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get().await()
                            
                            val chunkUsers = usersSnapshot.toObjects(User::class.java)
                            users.addAll(chunkUsers)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching user details chunk", e)
                        }
                    }
                    
                    // Sort the fetched users to match the original recent chats ordering
                    users.sortBy { otherUserRollNumbers.indexOf(it.id) }
                }
                
                _uiState.update { it.copy(recentUsers = users) }
                Log.d(TAG, "Loaded ${users.size} active chat users for $userRollNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent chats", e)
                _uiState.update { it.copy(recentUsers = emptyList()) }
            }
        }
    }

    private fun loadCommunities() {
        val currentUserRollNumber = sessionManager.fetchRollNumber() ?: return

        lifecycleScope.launch {
            try {
                // Load both regular and subject-based groups
                val allGroups = groupRepository.getAllGroupsForUser(currentUserRollNumber)
                _uiState.update { it.copy(communities = allGroups, isLoading = false) }
                Log.d(TAG, "Loaded ${allGroups.size} total groups for user $currentUserRollNumber")
            } catch (e: Exception) {
                _uiState.update { it.copy(communities = emptyList(), isLoading = false) }
                Log.e(TAG, "Error loading communities", e)
                Toast.makeText(requireContext(), "Failed to load communities.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUnreadMessagesPopup() {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_unread_messages, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val recyclerDirectMessages = popupView.findViewById<RecyclerView>(R.id.recycler_direct_messages)
        val recyclerGroupMessages = popupView.findViewById<RecyclerView>(R.id.recycler_group_messages)
        val textNoDirectMessages = popupView.findViewById<TextView>(R.id.text_no_direct_messages)
        val textNoGroupMessages = popupView.findViewById<TextView>(R.id.text_no_group_messages)

        recyclerDirectMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerGroupMessages.layoutManager = LinearLayoutManager(requireContext())

        val directMessageAdapter = UnreadMessageAdapter(requireContext(), false)
        val groupMessageAdapter = UnreadMessageAdapter(requireContext(), true)

        recyclerDirectMessages.adapter = directMessageAdapter
        recyclerGroupMessages.adapter = groupMessageAdapter

        lifecycleScope.launch {
            val repo = UnreadMessageRepository(requireContext())
            val (directMessages, groupMessages) = repo.fetchAllUnreadMessages()

            if (directMessages.isEmpty()) {
                textNoDirectMessages.visibility = View.VISIBLE
                recyclerDirectMessages.visibility = View.GONE
            } else {
                textNoDirectMessages.visibility = View.GONE
                recyclerDirectMessages.visibility = View.VISIBLE
                directMessageAdapter.updateMessages(directMessages)
            }

            if (groupMessages.isEmpty()) {
                textNoGroupMessages.visibility = View.VISIBLE
                recyclerGroupMessages.visibility = View.GONE
            } else {
                textNoGroupMessages.visibility = View.GONE
                recyclerGroupMessages.visibility = View.VISIBLE
                groupMessageAdapter.updateMessages(groupMessages)
            }
        }

        popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
    }

    private fun showSearchDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_user_search)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val editSearch = dialog.findViewById<EditText>(R.id.edit_search)
        val btnClearSearch = dialog.findViewById<ImageButton>(R.id.btn_clear_search)
        val progressSearch = dialog.findViewById<ProgressBar>(R.id.progress_search)
        val recyclerSearchResults = dialog.findViewById<RecyclerView>(R.id.recycler_search_results)
        val textNoResults = dialog.findViewById<TextView>(R.id.text_no_results)

        recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())
        val searchAdapter = ChatSearchAdapter(
            requireContext(),
            emptyList(),
            sessionManager,
            onUserClick = { user ->
                dialog.dismiss()
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("currentUserRollNumber", sessionManager.fetchRollNumber())
                    putExtra("currentUserName", sessionManager.fetchUserName())
                    putExtra("otherUserRollNumber", user.id)
                    putExtra("otherUserName", user.name)
                }
                startActivity(intent)
            },
            onGroupClick = { group ->
                dialog.dismiss()
                val intent = Intent(requireContext(), GroupChatActivity::class.java).apply {
                    putExtra("GROUP_ID", group.id)
                    putExtra("GROUP_NAME", group.name)
                }
                startActivity(intent)
            }
        )
        recyclerSearchResults.adapter = searchAdapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                if (query.length >= 1) {
                    performSearch(query, searchAdapter, progressSearch, textNoResults, recyclerSearchResults)
                } else {
                    searchAdapter.updateData(emptyList())
                    textNoResults.visibility = View.GONE
                }
            }
        })
        
        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = editSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query, searchAdapter, progressSearch, textNoResults, recyclerSearchResults)
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
                }
                return@setOnEditorActionListener true
            }
            false
        }

        btnClearSearch.setOnClickListener { editSearch.setText("") }
        dialog.show()
    }
    
    private fun performSearch(
        query: String,
        adapter: ChatSearchAdapter,
        progressBar: ProgressBar,
        noResultsText: TextView,
        recyclerView: RecyclerView
    ) {
        progressBar.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                // Search users
                val userQuery = if (userType.equals("Admin", ignoreCase = true)) {
                    db.collection("users")
                } else {
                    db.collection("users").whereEqualTo("userType", "Admin")
                }
                val userSnapshot = userQuery.get().await()
                val userResults = userSnapshot.documents.mapNotNull { document ->
                    val user = document.toObject(User::class.java)
                    if (user != null && user.id != userRollNumber &&
                        (user.name.contains(query, ignoreCase = true) || user.rollNumber.contains(query, ignoreCase = true))) {
                        ChatSearchResult.UserResult(user)
                    } else null
                }
                // Search groups
                val groupSnapshot = db.collection("groups").get().await()
                val groupResults = groupSnapshot.documents.mapNotNull { document ->
                    val group = document.toObject(Group::class.java)
                    if (group != null && group.participants.contains(userRollNumber) &&
                        (group.name.contains(query, ignoreCase = true) || group.id.contains(query, ignoreCase = true))) {
                        ChatSearchResult.GroupResult(group)
                    } else null
                }
                val allResults = (userResults + groupResults).sortedWith(compareBy {
                    when (it) {
                        is ChatSearchResult.UserResult -> it.user.name
                        is ChatSearchResult.GroupResult -> it.group.name
                    }
                })
                progressBar.visibility = View.GONE
                if (allResults.isEmpty()) {
                    noResultsText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    noResultsText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateData(allResults)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching users and groups", e)
                progressBar.visibility = View.GONE
                noResultsText.text = "Error searching. Try again."
                noResultsText.visibility = View.VISIBLE
            }
        }
    }
} 