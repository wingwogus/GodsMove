package com.godsmove.api.auth.controller

import com.godsmove.api.exception.GlobalExceptionHandler
import com.godsmove.application.auth.AuthCommand
import com.godsmove.application.auth.AuthResult
import com.godsmove.application.auth.AuthService
import com.godsmove.application.auth.KakaoLoginService
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
@TestPropertySource(properties = ["app.auth.refresh-cookie-secure=true"])
class AuthControllerBusinessTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var kakaoLoginService: KakaoLoginService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `login returns token pair from service`() {
        `when`(authService.login(AuthCommand.Login("user@example.com", "password123")))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
    }

    @Test
    fun `send-code maps business exception to failure response`() {
        doThrow(object : BusinessException(ErrorCode.DUPLICATE_EMAIL) {})
            .`when`(authService)
            .sendVerificationCode(AuthCommand.SendVerificationCode("taken@example.com"))

        mockMvc.perform(
            post("/api/v1/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"taken@example.com"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_003")))
    }

    @Test
    fun `reissue returns rotated token pair and refresh cookie`() {
        `when`(authService.reissue(AuthCommand.Reissue("refresh-token")))
            .thenReturn(AuthResult.TokenPair("new-access-token", "new-refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        mockMvc.perform(
            post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"refresh-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken", equalTo("new-access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("new-refresh-token")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=new-refresh-token")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
    }

    @Test
    fun `kakao login returns token pair from service`() {
        `when`(kakaoLoginService.login(AuthCommand.KakaoLogin("id-token", "nonce")))
            .thenReturn(kakaoLoginResult())

        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"id-token","nonce":"nonce"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
    }

    @Test
    fun `kakao login maps invalid token to unauthorized`() {
        doThrow(object : BusinessException(ErrorCode.INVALID_KAKAO_TOKEN) {})
            .`when`(kakaoLoginService)
            .login(AuthCommand.KakaoLogin("bad-token", "nonce"))

        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"bad-token","nonce":"nonce"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_009")))
    }

    private fun kakaoLoginResult(): AuthResult.Login {
        return AuthResult.Login(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            member = memberProfile(),
            onboarding = AuthResult.Onboarding(AuthResult.OnboardingStatus.REQUIRED)
        )
    }

    private fun memberProfile(): AuthResult.MemberProfile {
        val managementTypeClass = Class.forName("com.godsmove.domain.member.ManagementType")
        val managementType = managementTypeClass.enumConstants
            .first { (it as Enum<*>).name == "UNREGISTERED" }

        return AuthResult.MemberProfile::class.java
            .getDeclaredConstructor(
                UUID::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                LocalDate::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                managementTypeClass
            )
            .newInstance(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "kakao@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                managementType
            )
    }
}
