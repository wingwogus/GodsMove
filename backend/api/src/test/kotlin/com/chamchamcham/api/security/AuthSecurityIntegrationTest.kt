package com.chamchamcham.api.security

import com.chamchamcham.api.testsupport.signedTestToken
import com.chamchamcham.application.security.TokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
class AuthSecurityIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenProvider: TokenProvider
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")

    @Test
    fun `auth endpoints are publicly accessible`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invalid","password":"password123"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `kakao auth endpoint is publicly accessible`() {
        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"","nonce":"nonce"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `apple auth endpoint is publicly accessible`() {
        mockMvc.perform(
            post("/api/v1/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identityToken":"","nonce":"nonce"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `naver auth endpoint is publicly accessible`() {
        mockMvc.perform(
            post("/api/v1/auth/naver/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessToken":""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `onboarding complete requires authentication`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"홍길동",
                      "phone":"010-1234-5678",
                      "birthDate":"1990-01-01",
                      "nickname":"길동",
                      "region":"서울",
                      "experienceLevel":"BEGINNER"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `oauth redirect routes are not public auth routes`() {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoint rejects missing token`() {
        mockMvc.perform(get("/api/v1/test/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `cors preflight allows configured origin`() {
        mockMvc.perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
    }

    @Test
    fun `cors preflight rejects unconfigured origin`() {
        mockMvc.perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `protected api denial is logged with final status guest member and forwarded client ip`(
        output: CapturedOutput
    ) {
        mockMvc.perform(
            get("/api/v1/test/me")
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.2")
        )
            .andExpect(status().isUnauthorized)

        val logs = output.out + output.err
        assertThat(logs)
            .contains("[REQ START]")
            .contains("[REQ END]")
            .contains("status=401")
            .contains("WARN")
            .contains("client=203.0.113.7")
            .contains("[memberId=GUEST]")
            .contains("[ip=203.0.113.7]")

        assertThat(MDC.get("traceId")).isNull()
        assertThat(MDC.get("eventId")).isNull()
        assertThat(MDC.get("memberId")).isNull()
        assertThat(MDC.get("clientIp")).isNull()
    }

    @Test
    fun `protected api denial uses proxy client ip when forwarded header is absent`(
        output: CapturedOutput
    ) {
        mockMvc.perform(
            get("/api/v1/test/me")
                .header("X-Forwarded-For", "unknown")
                .header("Proxy-Client-IP", "198.51.100.9")
        )
            .andExpect(status().isUnauthorized)

        val logs = output.out + output.err
        assertThat(logs)
            .contains("[REQ START]")
            .contains("[REQ END]")
            .contains("status=401")
            .contains("client=198.51.100.9")
            .contains("[ip=198.51.100.9]")
    }

    @Test
    fun `public remote address ignores spoofed forwarded client ip`(
        output: CapturedOutput
    ) {
        mockMvc.perform(
            get("/api/v1/test/me")
                .with { request ->
                    request.remoteAddr = "198.51.100.10"
                    request
                }
                .header("X-Forwarded-For", "203.0.113.7")
        )
            .andExpect(status().isUnauthorized)

        val logs = output.out + output.err
        assertThat(logs)
            .contains("[REQ START]")
            .contains("[REQ END]")
            .contains("status=401")
            .contains("client=198.51.100.10")
            .contains("[ip=198.51.100.10]")
            .doesNotContain("client=203.0.113.7")
            .doesNotContain("[ip=203.0.113.7]")
    }

    @Test
    fun `protected endpoint accepts valid jwt`() {
        val accessToken = tokenProvider.createAccessToken(memberId, "ROLE_USER")

        mockMvc.perform(
            get("/api/v1/test/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(memberId.toString())))
    }

    @Test
    fun `protected endpoint rejects signed access token with non uuid subject`() {
        val accessToken = signedTestToken(subject = "42", tokenType = "access", role = "ROLE_USER")

        mockMvc.perform(
            get("/api/v1/test/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh token cannot authenticate protected endpoint`() {
        val refreshToken = tokenProvider.createRefreshToken(memberId)

        mockMvc.perform(
            get("/api/v1/test/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout requires authentication`() {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized)
    }

}
