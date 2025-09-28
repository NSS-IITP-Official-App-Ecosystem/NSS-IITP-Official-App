package com.phad.chatapp.models

sealed class ChatSearchResult {
    data class UserResult(val user: User) : ChatSearchResult()
    data class GroupResult(val group: Group) : ChatSearchResult()
} 