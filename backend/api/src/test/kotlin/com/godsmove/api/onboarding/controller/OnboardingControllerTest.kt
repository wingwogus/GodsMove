package com.godsmove.api.onboarding.controller

import com.godsmove.api.exception.GlobalExceptionHandler
import com.godsmove.application.auth.AuthCommand
import com.godsmove.application.auth.AuthResult
import com.godsmove.application.auth.OnboardingService
import com.godsmove.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(OnboardingController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class OnboardingControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var onboardingService: OnboardingService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `complete returns onboarding complete response`() {
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
            post("/api/v1/onboarding/complete")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.member.id", equalTo(memberId.toString())))
            .andExpect(jsonPath("$.data.member.nickname", equalTo("길동")))
            .andExpect(jsonPath("$.data.member.managementType", equalTo("REGISTERED")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("COMPLETE")))
    }

    @Test
    fun `complete rejects blank nickname`() {
        mockMvc.perform(
            post("/api/v1/onboarding/complete")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody().replace("길동", ""))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("nickname")))
    }

    @Test
    fun `complete rejects malformed principal`() {
        mockMvc.perform(
            post("/api/v1/onboarding/complete")
                .with(authenticatedMember("not-a-uuid"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun onboardingCompleteResult(): AuthResult.OnboardingComplete {
        return AuthResult.OnboardingComplete(
            member = memberProfile(),
            onboarding = AuthResult.Onboarding(AuthResult.OnboardingStatus.COMPLETE)
        )
    }

    private fun memberProfile(): AuthResult.MemberProfile {
        val managementTypeClass = Class.forName("com.godsmove.domain.member.ManagementType")
        val managementType = managementTypeClass.enumConstants
            .first { (it as Enum<*>).name == "REGISTERED" }

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
                memberId,
                "member@example.com",
                "홍길동",
                "010-1234-5678",
                LocalDate.parse("1990-01-01"),
                "길동",
                "서울",
                "BEGINNER",
                managementType
            )
    }

    private fun validRequestBody(): String {
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
