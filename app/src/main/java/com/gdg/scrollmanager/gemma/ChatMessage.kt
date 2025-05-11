package com.gdg.scrollmanager.gemma

import java.util.UUID

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"
const val THINKING_MARKER_END = "</think>"

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val rawMessage: String = "",
    val author: String = USER_PREFIX,
    val isThinking: Boolean = false,
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean
        get() = rawMessage.isEmpty()
        
    val isFromUser: Boolean
        get() = author == USER_PREFIX
        
    val message: String
        get() = rawMessage.replace(THINKING_MARKER_END, "")
}