package com.chamchamcham.api.voice

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.voice.RealtimeSessionProvider
import com.chamchamcham.application.voice.RealtimeSessionRequest
import com.chamchamcham.application.voice.RealtimeSessionResult
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * OpenAI Realtime API의 POST /v1/realtime/client_secrets를 호출해 iOS가 WebRTC로 직접
 * 붙을 수 있는 단기 유효 토큰을 발급받는 어댑터. 오디오/실시간 대화는 이 백엔드를 거치지
 * 않고 iOS ↔ OpenAI가 직접 처리하므로, 이 클래스가 유일한 OpenAI 연동 지점이다.
 */
@Component
class OpenAiRealtimeSessionProvider internal constructor(
    private val restClient: RestClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
) : RealtimeSessionProvider {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: OpenAiRealtimeProperties
    ) : this(
        restClientBuilder
            .requestFactory(
                createRequestFactory(properties.connectTimeoutMillis, properties.readTimeoutMillis)
            )
            .build(),
        properties.baseUrl,
        properties.apiKey,
        properties.model,
        properties.voice,
    )

    override fun createEphemeralSession(request: RealtimeSessionRequest): RealtimeSessionResult {
        val body = mapOf(
            "session" to mapOf(
                "type" to "realtime",
                "model" to model,
                "instructions" to request.instructions,
                "audio" to mapOf("output" to mapOf("voice" to voice)),
                "tools" to request.tools,
            )
        )

        val response = try {
            restClient.post()
                .uri("$baseUrl/v1/realtime/client_secrets")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ClientSecretResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
        }

        val clientSecret = response?.value ?: throw BusinessException(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
        val expiresAt = response.expiresAt
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) }
            ?: LocalDateTime.now().plusMinutes(DEFAULT_EXPIRY_MINUTES)

        return RealtimeSessionResult(clientSecret = clientSecret, expiresAt = expiresAt, model = model)
    }

    private data class ClientSecretResponse(
        @JsonProperty("value") val value: String? = null,
        @JsonProperty("expires_at") val expiresAt: Long? = null,
    )

    companion object {
        private const val DEFAULT_EXPIRY_MINUTES = 10L

        private fun createRequestFactory(
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int
        ): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMillis)
                setReadTimeout(readTimeoutMillis)
            }
    }
}
