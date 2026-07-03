package com.chamchamcham.api.auth.dto

import com.chamchamcham.application.auth.common.AuthResult
import java.time.LocalDate
import java.util.UUID

object AuthResponses {
    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String
    ) {
        companion object {
            fun from(result: AuthResult.TokenPair): TokenResponse {
                return TokenResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken
                )
            }
        }
    }

    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val member: MemberProfileResponse,
        val onboarding: OnboardingResponse
    ) {
        companion object {
            fun from(result: AuthResult.Login): LoginResponse {
                return LoginResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    member = MemberProfileResponse.from(result.member),
                    onboarding = OnboardingResponse.from(result.onboarding)
                )
            }
        }
    }

    data class OnboardingCompleteResponse(
        val member: MemberProfileResponse,
        val onboarding: OnboardingResponse
    ) {
        companion object {
            fun from(result: AuthResult.OnboardingComplete): OnboardingCompleteResponse {
                return OnboardingCompleteResponse(
                    member = MemberProfileResponse.from(result.member),
                    onboarding = OnboardingResponse.from(result.onboarding)
                )
            }
        }
    }

    data class MemberProfileResponse(
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
            fun from(result: AuthResult.MemberProfile): MemberProfileResponse {
                return MemberProfileResponse(
                    id = result.id,
                    email = result.email,
                    name = result.name,
                    phone = result.phone,
                    birthDate = result.birthDate,
                    nickname = result.nickname,
                    region = result.region,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType
                )
            }
        }
    }

    data class OnboardingResponse(
        val status: AuthResult.OnboardingStatus,
        val missingFields: List<AuthResult.OnboardingField>
    ) {
        companion object {
            fun from(result: AuthResult.Onboarding): OnboardingResponse {
                return OnboardingResponse(
                    status = result.status,
                    missingFields = result.missingFields
                )
            }
        }
    }
}
