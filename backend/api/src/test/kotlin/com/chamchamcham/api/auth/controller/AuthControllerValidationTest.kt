package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.auth.common.OnboardingService
import com.chamchamcham.application.auth.local.AuthService
import com.chamchamcham.application.auth.social.AppleLoginService
import com.chamchamcham.application.auth.social.KakaoLoginService
import com.chamchamcham.application.auth.social.NaverLoginService
import com.chamchamcham.application.security.TokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.`when`
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
import java.util.UUID

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
@ExtendWith(OutputCaptureExtension::class)
class AuthControllerValidationTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000101")

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
    fun `kakao login accepts missing access token`() {
        `when`(kakaoLoginService.login(AuthCommand.KakaoLogin("id-token", "nonce", null)))
            .thenReturn(loginResult())

        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"id-token","nonce":"nonce"}""")
        )
            .andExpect(status().isOk)
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
                      "experienceLevel":3,
                      "managementType":"AGRICULTURAL_INDIVIDUAL",
                      "farmName":"길동농장",
                      "farmAddress":"서울시 강남구",
                      "cropIds":["$cropId"]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("nickname")))
    }

    @Test
    fun `complete onboarding rejects experience level below zero`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody(experienceLevel = -1))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("experienceLevel")))
    }

    @Test
    fun `complete onboarding rejects experience level above one hundred`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody(experienceLevel = 101))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("experienceLevel")))
    }

    @Test
    fun `complete onboarding rejects empty crop ids`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody(cropIdsJson = "[]"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("cropIds")))
    }

    @Test
    fun `complete onboarding rejects invalid management type`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody(managementType = "INVALID_TYPE"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_002")))
    }

    private fun loginResult(): AuthResult.Login {
        return AuthResult.Login(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            member = AuthResult.MemberProfile(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                email = null,
                name = null,
                phone = null,
                birthDate = null,
                nickname = null,
                experienceLevel = null,
                managementType = null
            ),
            onboarding = AuthResult.Onboarding(
                status = AuthResult.OnboardingStatus.REQUIRED,
                missingFields = emptyList()
            )
        )
    }

    private fun validOnboardingRequestBody(
        experienceLevel: Int = 3,
        managementType: String = "AGRICULTURAL_INDIVIDUAL",
        cropIdsJson: String = """["$cropId"]"""
    ): String {
        return """
            {
              "name":"홍길동",
              "phone":"010-1234-5678",
              "birthDate":"1990-01-01",
              "nickname":"길동",
              "experienceLevel":$experienceLevel,
              "managementType":"$managementType",
              "farmName":"길동농장",
              "farmAddress":"서울시 강남구",
              "cropIds":$cropIdsJson
            }
        """.trimIndent()
    }
}
