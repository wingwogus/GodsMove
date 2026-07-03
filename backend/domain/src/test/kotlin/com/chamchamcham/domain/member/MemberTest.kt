package com.chamchamcham.domain.member

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MemberTest {
    @Test
    fun `social member can be created without email`() {
        val member = Member(
            email = null,
            passwordHash = null
        )

        assertNull(member.email)
        assertNull(member.passwordHash)
        assertEquals(MemberStatus.ACTIVE, member.status)
    }

    @Test
    fun `complete onboarding stores profile without member region`() {
        val member = Member(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            email = "member@example.com",
            passwordHash = null
        )

        member.completeOnboarding(
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 1, 1),
            nickname = "길동",
            experienceLevel = 72,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        )

        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 1, 1), member.birthDate)
        assertEquals("길동", member.nickname)
        assertEquals(72, member.experienceLevel)
        assertEquals(ManagementType.AGRICULTURAL_INDIVIDUAL, member.managementType)
    }

    @Test
    fun `prefillProfile fills missing blank name phone and null birthDate`() {
        val member = member(
            name = " ",
            phone = "",
            birthDate = null
        )

        member.prefillProfile(
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 5, 10)
        )

        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 5, 10), member.birthDate)
    }

    @Test
    fun `prefillProfile preserves existing nonblank name phone and existing birthDate`() {
        val member = member(
            name = "기존 이름",
            phone = "010-0000-0000",
            birthDate = LocalDate.of(1980, 1, 1)
        )

        member.prefillProfile(
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 5, 10)
        )

        assertEquals("기존 이름", member.name)
        assertEquals("010-0000-0000", member.phone)
        assertEquals(LocalDate.of(1980, 1, 1), member.birthDate)
    }

    private fun member(
        name: String? = null,
        phone: String? = null,
        birthDate: LocalDate? = null,
        nickname: String? = null,
        experienceLevel: Int? = null
    ): Member {
        return Member(
            email = "member@example.com",
            name = name,
            phone = phone,
            birthDate = birthDate,
            nickname = nickname,
            experienceLevel = experienceLevel,
            passwordHash = null
        )
    }
}
