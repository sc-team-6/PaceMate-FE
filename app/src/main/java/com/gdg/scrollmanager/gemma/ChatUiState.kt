package com.gdg.scrollmanager.gemma

class UiState(val thinking: Boolean) {
    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage>
        get() = _messages.toList()

    private var _loadingMessage: ChatMessage? = null

    fun addMessage(message: String, prefix: String) {
        // If there's a loading message, remove it
        if (_loadingMessage != null) {
            _messages.remove(_loadingMessage)
            _loadingMessage = null
        }
        // Add a new message
        _messages.add(
            0,
            ChatMessage(
                message = message,
                isFromUser = prefix == USER_PREFIX,
                isThinking = thinking && prefix == MODEL_PREFIX
            )
        )
    }

    fun createLoadingMessage() {
        _loadingMessage = ChatMessage(
            message = "",
            isFromUser = false,
            isLoading = true
        )
        _messages.add(0, _loadingMessage!!)
    }

    fun appendMessage(partialResult: String) {
        val lastMessage = _messages.firstOrNull { !it.isFromUser }
        if (lastMessage != null) {
            _messages.remove(lastMessage)
            _messages.add(
                0,
                ChatMessage(
                    id = lastMessage.id,
                    message = partialResult,
                    isFromUser = false,
                    isThinking = thinking,
                    isLoading = false
                )
            )
        }
    }
}