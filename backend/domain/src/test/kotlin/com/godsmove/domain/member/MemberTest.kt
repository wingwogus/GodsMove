package com.godsmove.domain.member

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MemberTest {
    @Test
    fun `completeOnboarding overwrites all onboarding profile fields`() {
        val member = member(
            name = "기존 이름",
            phone = "010-0000-0000",
            birthDate = LocalDate.of(1980, 1, 1),
            nickname = "기존 닉네임",
            region = "부산",
            experienceLevel = "ADVANCED"
        )

        member.completeOnboarding(
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 5, 10),
            nickname = "길동",
            region = "서울",
            experienceLevel = "BEGINNER"
        )

        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 5, 10), member.birthDate)
        assertEquals("길동", member.nickname)
        assertEquals("서울", member.region)
        assertEquals("BEGINNER", member.experienceLevel)
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
        region: String? = null,
        experienceLevel: String? = null
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
