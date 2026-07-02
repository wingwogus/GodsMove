package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OnboardingServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Mock
    private lateinit var memberRepository: MemberRepository

    private lateinit var onboardingStatusResolver: OnboardingStatusResolver
    private lateinit var service: OnboardingService

    @BeforeEach
    fun setUp() {
        onboardingStatusResolver = OnboardingStatusResolver()
        service = OnboardingService(memberRepository, onboardingStatusResolver)
    }

    @Test
    fun `complete stores all profile fields and returns complete`() {
        val member = Member(
            id = memberId,
            email = "user@example.com",
            passwordHash = null
        )
        val command = completeOnboardingCommand()

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))

        val result = service.complete(command)

        assertEquals(memberId, result.member.id)
        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 1, 1), member.birthDate)
        assertEquals("길동", member.nickname)
        assertEquals("서울", member.region)
        assertEquals("BEGINNER", member.experienceLevel)
        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.onboarding.status)
    }

    @Test
    fun `complete rejects missing member`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.complete(completeOnboardingCommand())
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    private fun completeOnboardingCommand(): AuthCommand.CompleteOnboarding {
        return AuthCommand.CompleteOnboarding(
            memberId = memberId,
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 1, 1),
            nickname = "길동",
            region = "서울",
            experienceLevel = "BEGINNER"
        )
    }
}
