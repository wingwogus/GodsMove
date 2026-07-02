package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.auth.common.OnboardingService
import com.chamchamcham.application.auth.local.AuthService
import com.chamchamcham.application.auth.social.AppleLoginService
import com.chamchamcham.application.auth.social.KakaoLoginService
import com.chamchamcham.application.auth.social.NaverLoginService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
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
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

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
    fun `kakao login returns login response from service`() {
        `when`(kakaoLoginService.login(AuthCommand.KakaoLogin("id-token", "nonce")))
            .thenReturn(loginResult())

        mockMvc.perform(
            post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"id-token","nonce":"nonce"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
            .andExpect(jsonPath("$.data.member.id", equalTo("00000000-0000-0000-0000-000000000001")))
            .andExpect(jsonPath("$.data.member.email", equalTo("social@example.com")))
            .andExpect(jsonPath("$.data.member.managementType", equalTo("UNREGISTERED")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[0]", equalTo("NAME")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[1]", equalTo("PHONE")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[2]", equalTo("BIRTH_DATE")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[3]", equalTo("NICKNAME")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[4]", equalTo("REGION")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[5]", equalTo("EXPERIENCE_LEVEL")))
    }

    @Test
    fun `apple login returns login response from service`() {
        `when`(
            appleLoginService.login(
                AuthCommand.AppleLogin(
                    identityToken = "identity-token",
                    nonce = "nonce",
                    authorizationCode = "authorization-code",
                    userIdentifier = "apple-user-id"
                )
            )
        ).thenReturn(loginResult())

        mockMvc.perform(
            post("/api/v1/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "identityToken":"identity-token",
                      "nonce":"nonce",
                      "authorizationCode":"authorization-code",
                      "userIdentifier":"apple-user-id"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
            .andExpect(jsonPath("$.data.member.email", equalTo("social@example.com")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[0]", equalTo("NAME")))
    }

    @Test
    fun `naver login returns login response from service`() {
        `when`(naverLoginService.login(AuthCommand.NaverLogin("naver-access-token")))
            .thenReturn(loginResult())

        mockMvc.perform(
            post("/api/v1/auth/naver/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessToken":"naver-access-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
            .andExpect(jsonPath("$.data.member.email", equalTo("social@example.com")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
            .andExpect(jsonPath("$.data.onboarding.missingFields[0]", equalTo("NAME")))
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

    @Test
    fun `complete onboarding returns onboarding complete response`() {
        `when`(
            onboardingService.complete(
                AuthCommand.CompleteOnboarding(
                    memberId = memberId,
                    name = "홍길동",
                    phone = "010-1234-5678",
                    birthDate = LocalDate.parse("1990-01-01"),
                    nickname = "길동",
                    region = "서울",
                    experienceLevel = "BEGINNER"
                )
            )
        ).thenReturn(onboardingCompleteResult())

        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.member.id", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.member.nickname", equalTo("길동")))
            .andExpect(jsonPath("$.data.member.managementType", equalTo("REGISTERED")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("COMPLETE")))
            .andExpect(jsonPath("$.data.onboarding.missingFields").isEmpty())
    }

    @Test
    fun `complete onboarding rejects malformed principal`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .with(authenticatedMember("not-a-uuid"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOnboardingRequestBody())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun loginResult(): AuthResult.Login {
        return AuthResult.Login(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            member = memberProfile(),
            onboarding = AuthResult.Onboarding(
                status = AuthResult.OnboardingStatus.REQUIRED,
                missingFields = listOf(
                    AuthResult.OnboardingField.NAME,
                    AuthResult.OnboardingField.PHONE,
                    AuthResult.OnboardingField.BIRTH_DATE,
                    AuthResult.OnboardingField.NICKNAME,
                    AuthResult.OnboardingField.REGION,
                    AuthResult.OnboardingField.EXPERIENCE_LEVEL
                )
            )
        )
    }

    private fun memberProfile(): AuthResult.MemberProfile {
        return AuthResult.MemberProfile(
            id = memberId,
            email = "social@example.com",
            name = null,
            phone = null,
            birthDate = null,
            nickname = null,
            region = null,
            experienceLevel = null,
            managementType = "UNREGISTERED"
        )
    }

    private fun onboardingCompleteResult(): AuthResult.OnboardingComplete {
        return AuthResult.OnboardingComplete(
            member = completedMemberProfile(),
            onboarding = AuthResult.Onboarding(
                status = AuthResult.OnboardingStatus.COMPLETE,
                missingFields = emptyList()
            )
        )
    }

    private fun completedMemberProfile(): AuthResult.MemberProfile {
        return AuthResult.MemberProfile(
            id = memberId,
            email = "member@example.com",
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.parse("1990-01-01"),
            nickname = "길동",
            region = "서울",
            experienceLevel = "BEGINNER",
            managementType = "REGISTERED"
        )
    }

    private fun validOnboardingRequestBody(): String {
        return """
            {
              "name":"홍길동",
              "phone":"010-1234-5678",
              "birthDate":"1990-01-01",
              "nickname":"길동",
              "region":"서울",
              "experienceLevel":"BEGINNER"
            }
        """.trimIndent()
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                memberId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
            request
        }
    }
}
