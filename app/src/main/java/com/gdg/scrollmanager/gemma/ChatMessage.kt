package com.gdg.scrollmanager.gemma

import java.util.UUID

const val USER_PREFIX = "User: "
const val MODEL_PREFIX = "Model: "

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String = "",
    val isFromUser: Boolean = true,
    val isThinking: Boolean = false,
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean
        get() = message.isEmpty()
    val rawMessage: String
        get() = if (isFromUser) USER_PREFIX + message else MODEL_PREFIX + message
}