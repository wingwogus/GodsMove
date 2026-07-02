package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.auth.common.OnboardingService
import com.chamchamcham.application.auth.local.AuthService
import com.chamchamcham.application.auth.social.AppleLoginService
import com.chamchamcham.application.auth.social.KakaoLoginService
import com.chamchamcham.application.auth.social.NaverLoginService
import com.chamchamcham.application.security.TokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.junit.jupiter.api.extension.ExtendWith

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
@ExtendWith(OutputCaptureExtension::class)
class AuthControllerValidationTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var kakaoLoginService: KakaoLoginService

    @MockBean
    private lateinit var appleLoginService: AppleLoginService

    @MockBean
    private lateinit var naverLoginService: NaverLoginService

    @MockBean
    private lateinit var onboardingService: OnboardingService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `send-code rejects invalid email`() {
        mockMvc.perform(
            post("/api/v1/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"not-an-email"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `signup rejects short password without echoing rejected value`(output: CapturedOutput) {
        val result = mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"123"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("password")))
            .andReturn()

        assertThat(result.response.contentAsString).doesNotContain("123")
        assertThat(output.out + output.err).doesNotContain("123")
    }

    @Test
    fun `kakao login rejects missing id token`() {
        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"","nonce":"nonce"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `apple login rejects missing identity token`() {
        mockMvc.perform(
            post("/api/v1/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identityToken":"","nonce":"nonce"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `naver login rejects missing access token`() {
        mockMvc.perform(
            post("/api/v1/auth/naver/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessToken":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `complete onboarding rejects blank nickname`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"홍길동",
                      "phone":"010-1234-5678",
                      "birthDate":"1990-01-01",
                      "nickname":"",
                      "region":"서울",
                      "experienceLevel":"BEGINNER"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("nickname")))
    }
}
