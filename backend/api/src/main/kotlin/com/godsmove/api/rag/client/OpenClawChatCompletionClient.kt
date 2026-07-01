package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.ChatCompletionClient
import com.godsmove.application.coaching.rag.ChatMessage
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class OpenClawChatCompletionClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${openclaw.base-url}")
    baseUrl: String,
    @Value("\${openclaw.api-key}")
    private val apiKey: String,
    @Value("\${openclaw.agent-id}")
    private val agentId: String
) : ChatCompletionClient {
    private val restClient = restClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun complete(messages: List<ChatMessage>, model: String): String {
        if (apiKey.isBlank() || agentId.isBlank()) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }

        return try {
            val response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header("x-openclaw-agent-id", agentId)
                .body(OpenClawChatRequest(model = model, messages = messages, stream = false))
                .retrieve()
                .body(OpenClawChatResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)

            response.choices.firstOrNull()?.message?.content
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RestClientException) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }
    }
}

data class OpenClawChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean
)

data class OpenClawChatResponse(
    val choices: List<OpenClawChoice> = emptyList()
)

data class OpenClawChoice(
    val message: OpenClawMessage? = null
)

data class OpenClawMessage(
    val content: String? = null
)
