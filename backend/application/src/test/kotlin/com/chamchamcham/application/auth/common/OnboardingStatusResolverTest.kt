package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OnboardingStatusResolverTest {
    private val resolver = OnboardingStatusResolver()

    @Test
    fun `complete profile returns complete`() {
        val member = member(
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 1, 1),
            nickname = "길동",
            region = "서울",
            experienceLevel = "BEGINNER"
        )

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.status)
    }

    @Test
    fun `missing birth date returns required`() {
        val member = member(birthDate = null)

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
    }

    @Test
    fun `blank nickname returns required`() {
        val member = member(nickname = " ")

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
    }

    private fun member(
        name: String? = "홍길동",
        phone: String? = "010-1234-5678",
        birthDate: LocalDate? = LocalDate.of(1990, 1, 1),
        nickname: String? = "길동",
        region: String? = "서울",
        experienceLevel: String? = "BEGINNER"
    ): Member {
        return Member(
            email = "member@example.com",
            name = name,
            phone = phone,
            birthDate = birthDate,
            nickname = nickname,
            region = region,
            experienceLevel = experienceLevel,
            passwordHash = null
        )
    }
}
