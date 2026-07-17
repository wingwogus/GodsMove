package com.chamchamcham.api.voice

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.voice.RealtimeSessionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.io.IOException

class OpenAiRealtimeSessionProviderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var provider: OpenAiRealtimeSessionProvider

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        provider = OpenAiRealtimeSessionProvider(restClientBuilder.build(), BASE_URL, API_KEY, MODEL, VOICE)
    }

    @Test
    fun `정상 응답이면 ephemeral secret과 model을 반환한다`() {
        server.expect(requestTo("$BASE_URL/v1/realtime/client_secrets"))
            .andExpect(header("Authorization", "Bearer $API_KEY"))
            .andExpect(jsonPath("$.expires_after.anchor").value("created_at"))
            .andExpect(jsonPath("$.expires_after.seconds").value(EXPIRES_AFTER_SECONDS))
            .andRespond(
                withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"value":"ek_test_123","expires_at":1735689600}""")
            )

        val result = provider.createEphemeralSession(
            RealtimeSessionRequest(instructions = "안내", tools = listOf(mapOf("type" to "function")), expiresAfterSeconds = EXPIRES_AFTER_SECONDS)
        )

        assertThat(result.clientSecret).isEqualTo("ek_test_123")
        assertThat(result.model).isEqualTo(MODEL)
        server.verify()
    }

    @Test
    fun `전송 예외는 제공자 불가 예외로 변환된다`() {
        server.expect(requestTo("$BASE_URL/v1/realtime/client_secrets"))
            .andRespond(withException(IOException("connection reset")))

        val exception = assertThrows(BusinessException::class.java) {
            provider.createEphemeralSession(RealtimeSessionRequest(instructions = "안내", tools = emptyList(), expiresAfterSeconds = EXPIRES_AFTER_SECONDS))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `secret 값이 없으면 제공자 불가 예외를 던진다`() {
        server.expect(requestTo("$BASE_URL/v1/realtime/client_secrets"))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{}"))

        val exception = assertThrows(BusinessException::class.java) {
            provider.createEphemeralSession(RealtimeSessionRequest(instructions = "안내", tools = emptyList(), expiresAfterSeconds = EXPIRES_AFTER_SECONDS))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_PROVIDER_UNAVAILABLE)
    }

    companion object {
        private const val BASE_URL = "https://openai.example.test"
        private const val API_KEY = "test-api-key"
        private const val MODEL = "gpt-realtime"
        private const val VOICE = "marin"
        private const val EXPIRES_AFTER_SECONDS = 360
    }
}
