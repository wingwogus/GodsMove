package com.chamchamcham.api.auth.dto

import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.crop.CropResult
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
        val farm: FarmResponse,
        val crops: List<CropResponse>,
        val onboarding: OnboardingResponse
    ) {
        companion object {
            fun from(result: AuthResult.OnboardingComplete): OnboardingCompleteResponse {
                return OnboardingCompleteResponse(
                    member = MemberProfileResponse.from(result.member),
                    farm = FarmResponse.from(result.farm),
                    crops = result.crops.map(CropResponse::from),
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
        val experienceLevel: Int?,
        val managementType: String?
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
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType
                )
            }
        }
    }

    data class FarmResponse(
        val id: UUID,
        val name: String,
        val address: String
    ) {
        companion object {
            fun from(result: AuthResult.FarmSummary): FarmResponse {
                return FarmResponse(
                    id = result.id,
                    name = result.name,
                    address = result.address
                )
            }
        }
    }

    data class CropResponse(
        val id: UUID,
        val externalNo: Int,
        val name: String,
        val usePartCategory: String,
        val usePartCategoryLabel: String
    ) {
        companion object {
            fun from(result: CropResult.CropSummary): CropResponse {
                return CropResponse(
                    id = result.id,
                    externalNo = result.externalNo,
                    name = result.name,
                    usePartCategory = result.usePartCategory,
                    usePartCategoryLabel = result.usePartCategoryLabel
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
