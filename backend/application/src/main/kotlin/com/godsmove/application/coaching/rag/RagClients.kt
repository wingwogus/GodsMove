package com.godsmove.application.coaching.rag

data class ChatMessage(
    val role: String,
    val content: String
)

interface EmbeddingClient {
    fun embed(input: String, model: String): List<Double>
}

interface ChatCompletionClient {
    fun complete(messages: List<ChatMessage>, model: String): String
}
