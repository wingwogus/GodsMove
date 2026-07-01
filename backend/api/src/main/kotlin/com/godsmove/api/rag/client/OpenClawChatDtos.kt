package com.godsmove.api.rag.client

data class OpenClawCompletionRequest(
    val model: String,
    val messages: List<OpenClawCompletionMessage>,
    val stream: Boolean = false
)

data class OpenClawCompletionMessage(
    val role: String,
    val content: String
)

data class OpenClawCompletionResponse(
    val choices: List<OpenClawCompletionChoice> = emptyList()
)

data class OpenClawCompletionChoice(
    val message: OpenClawCompletionMessage? = null
)
