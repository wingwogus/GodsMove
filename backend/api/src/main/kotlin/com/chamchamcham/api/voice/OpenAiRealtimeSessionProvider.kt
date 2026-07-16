package com.chamchamcham.api.voice

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.voice.RealtimeSessionProvider
import com.chamchamcham.application.voice.RealtimeSessionRequest
import com.chamchamcham.application.voice.RealtimeSessionResult
import com.fasterxml.jackson.annotation.JsonProperty
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
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

    init {
        // 키가 비어 있거나 local/test 프로필의 placeholder면 모든 음성 세션 발급이 401로
        // 실패한다(VOICE_003으로만 보임). 기동 로그에서 바로 보이게 남긴다. local 개발을
        // 막지 않도록 예외는 던지지 않는다.
        if (apiKey.isBlank() || apiKey == PLACEHOLDER_API_KEY) {
            logger.warn {
                "OPENAI_API_KEY가 비어 있거나 placeholder입니다 — 음성 세션 발급이 전부 실패합니다(VOICE_003)."
            }
        }
    }

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
        } catch (exception: RestClientResponseException) {
            // 원인 없이 VOICE_003으로만 접으면 배포 로그에서 401(키)/429(쿼터)/400(요청 형식)을
            // 구분할 수 없다. OpenAI가 돌려준 상태·본문을 남긴다(본문에 비밀값 없음).
            logger.warn {
                "OpenAI realtime client_secrets 발급 거부: status=${exception.statusCode.value()} " +
                    "body=${exception.responseBodyAsString.take(500)}"
            }
            throw BusinessException(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
        } catch (exception: RestClientException) {
            logger.warn(exception) { "OpenAI realtime client_secrets 호출 실패(네트워크/타임아웃)" }
            throw BusinessException(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
        }

        val clientSecret = response?.value ?: run {
            logger.warn { "OpenAI realtime client_secrets 응답에 value가 없음: response=$response" }
            throw BusinessException(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
        }
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
        private val logger = KotlinLogging.logger {}
        private const val DEFAULT_EXPIRY_MINUTES = 10L
        /** application-local.yml / application-test.yml의 기본값. 실 발급은 불가능한 더미 키. */
        private const val PLACEHOLDER_API_KEY = "test-openai-api-key"

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
