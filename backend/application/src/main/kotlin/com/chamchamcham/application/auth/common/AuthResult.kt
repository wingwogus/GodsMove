package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.Member
import java.time.LocalDate
import java.util.UUID

object AuthResult {
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )

    data class Login(
        val accessToken: String,
        val refreshToken: String,
        val member: MemberProfile,
        val onboarding: Onboarding
    )

    data class OnboardingComplete(
        val member: MemberProfile,
        val onboarding: Onboarding
    )

    data class MemberProfile(
        val id: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val region: String?,
        val experienceLevel: String?,
        val managementType: String
    ) {
        companion object {
            fun from(member: Member): MemberProfile {
                return MemberProfile(
                    id = requireNotNull(member.id) { "Persisted member id is required" },
                    email = member.email,
                    name = member.name,
                    phone = member.phone,
                    birthDate = member.birthDate,
                    nickname = member.nickname,
                    region = member.region,
                    experienceLevel = member.experienceLevel,
                    managementType = member.managementType.name
                )
            }
        }
    }

    data class Onboarding(
        val status: OnboardingStatus,
        val missingFields: List<OnboardingField>
    )

    enum class OnboardingStatus {
        REQUIRED,
        COMPLETE
    }

    enum class OnboardingField {
        NAME,
        PHONE,
        BIRTH_DATE,
        NICKNAME,
        REGION,
        EXPERIENCE_LEVEL
    }
}
