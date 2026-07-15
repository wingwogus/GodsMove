package com.chamchamcham.api.rag.client

import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.config.OpenClawProperties
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@Primary
class OpenClawChatModel(
    restClientBuilder: RestClient.Builder,
    private val properties: OpenClawProperties,
    private val ragProperties: RagProperties
) : ChatModel {
    private val restClient = restClientBuilder
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeoutMillis)
                setReadTimeout(properties.readTimeoutMillis)
            }
        )
        .baseUrl(properties.baseUrl.trimEnd('/'))
        .build()

    override fun call(prompt: Prompt): ChatResponse {
        if (properties.apiKey.isBlank() || properties.agentId.isBlank()) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }

        return try {
            val response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .header("x-openclaw-agent-id", properties.agentId)
                .body(
                    OpenClawCompletionRequest(
                        model = ragProperties.chat.model,
                        messages = prompt.instructions.map { it.toOpenClawMessage() }
                    )
                )
                .retrieve()
                .body(OpenClawCompletionResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)

            val content = response.choices.firstOrNull()?.message?.content
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
            ChatResponse(listOf(Generation(AssistantMessage(content))))
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RestClientException) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }
    }

    private fun Message.toOpenClawMessage(): OpenClawCompletionMessage {
        return OpenClawCompletionMessage(role = messageType.value, content = text)
    }
}
