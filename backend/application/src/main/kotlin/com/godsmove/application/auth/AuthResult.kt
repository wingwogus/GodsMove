package com.godsmove.application.auth

import com.godsmove.domain.member.ManagementType
import com.godsmove.domain.member.Member
import java.time.LocalDate

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
        val id: String,
        val email: String,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val region: String?,
        val experienceLevel: String?,
        val managementType: ManagementType
    ) {
        companion object {
            fun from(member: Member): MemberProfile {
                return MemberProfile(
                    id = requireNotNull(member.id) { "Persisted member id is required" }.toString(),
                    email = member.email,
                    name = member.name,
                    phone = member.phone,
                    birthDate = member.birthDate,
                    nickname = member.nickname,
                    region = member.region,
                    experienceLevel = member.experienceLevel,
                    managementType = member.managementType
                )
            }
        }
    }

    data class Onboarding(
        val status: OnboardingStatus
    )

    enum class OnboardingStatus {
        REQUIRED,
        COMPLETE
    }
}
