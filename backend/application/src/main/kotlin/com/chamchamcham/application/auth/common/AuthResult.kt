package com.chamchamcham.application.auth.common

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.domain.farm.Farm
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
        val farm: FarmSummary,
        val crops: List<CropResult.CropSummary>,
        val onboarding: Onboarding
    )

    data class MemberProfile(
        val id: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?
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
                    experienceLevel = member.experienceLevel,
                    managementType = member.managementType?.name
                )
            }
        }
    }

    data class FarmSummary(
        val id: UUID,
        val name: String,
        val address: String
    ) {
        companion object {
            fun from(farm: Farm): FarmSummary {
                return FarmSummary(
                    id = requireNotNull(farm.id) { "Persisted farm id is required" },
                    name = farm.name,
                    address = farm.address
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
        EXPERIENCE_LEVEL,
        MANAGEMENT_TYPE
    }
}
