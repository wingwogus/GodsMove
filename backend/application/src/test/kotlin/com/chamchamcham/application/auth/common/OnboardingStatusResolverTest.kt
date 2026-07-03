package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class OnboardingStatusResolverTest {
    @Test
    fun `resolve returns missing fields for incomplete member`() {
        val member = Member(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            email = null,
            name = "홍길동",
            phone = null,
            birthDate = LocalDate.of(1998, 3, 12),
            nickname = null,
            region = null,
            experienceLevel = null,
            passwordHash = null
        )

        val result = OnboardingStatusResolver().resolve(member)

        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
        assertEquals(
            listOf(
                AuthResult.OnboardingField.PHONE,
                AuthResult.OnboardingField.NICKNAME,
                AuthResult.OnboardingField.REGION,
                AuthResult.OnboardingField.EXPERIENCE_LEVEL
            ),
            result.missingFields
        )
    }

    @Test
    fun `resolve returns complete when all required onboarding fields exist`() {
        val member = Member(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            email = null,
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1998, 3, 12),
            nickname = "농부",
            region = "전남",
            experienceLevel = "BEGINNER",
            passwordHash = null
        )

        val result = OnboardingStatusResolver().resolve(member)

        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.status)
        assertTrue(result.missingFields.isEmpty())
    }
}
