package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.EmbeddingClient
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class OllamaEmbeddingClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${ollama.base-url}")
    baseUrl: String
) : EmbeddingClient {
    private val restClient = restClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun embed(input: String, model: String): List<Double> {
        return try {
            val response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(OllamaEmbedRequest(model = model, input = input))
                .retrieve()
                .body(OllamaEmbedResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)

            response.embeddings?.firstOrNull()
                ?: response.embedding
                ?: throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RestClientException) {
            throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)
        }
    }
}

data class OllamaEmbedRequest(
    val model: String,
    val input: String
)

data class OllamaEmbedResponse(
    val embeddings: List<List<Double>>? = null,
    val embedding: List<Double>? = null
)
