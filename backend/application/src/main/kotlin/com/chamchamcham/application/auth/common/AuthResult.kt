package com.chamchamcham.application.auth.common

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmDataSource
import com.chamchamcham.domain.member.Member
import java.math.BigDecimal
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
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<FarmBoundaryCoordinateSummary>,
        val dataSource: FarmDataSourceSummary
    ) {
        companion object {
            fun from(farm: Farm): FarmSummary {
                return FarmSummary(
                    id = requireNotNull(farm.id) { "Persisted farm id is required" },
                    name = farm.name,
                    roadAddress = farm.roadAddress,
                    jibunAddress = farm.jibunAddress,
                    latitude = farm.latitude,
                    longitude = farm.longitude,
                    pnu = farm.pnu,
                    landCategory = farm.landCategory,
                    areaSqm = farm.areaSqm,
                    areaIsManualEntry = farm.areaIsManualEntry,
                    boundaryCoordinates = farm.boundaryCoordinates.map(FarmBoundaryCoordinateSummary::from),
                    dataSource = FarmDataSourceSummary.from(farm.dataSource)
                )
            }
        }
    }

    data class FarmBoundaryCoordinateSummary(
        val latitude: Double,
        val longitude: Double
    ) {
        companion object {
            fun from(coordinate: FarmBoundaryCoordinate): FarmBoundaryCoordinateSummary {
                return FarmBoundaryCoordinateSummary(
                    latitude = requireNotNull(coordinate.latitude) { "Boundary latitude is required" },
                    longitude = requireNotNull(coordinate.longitude) { "Boundary longitude is required" }
                )
            }
        }
    }

    data class FarmDataSourceSummary(
        val address: String?,
        val coordinate: String?,
        val parcel: String?,
        val landCharacteristic: String?
    ) {
        companion object {
            fun from(dataSource: FarmDataSource): FarmDataSourceSummary {
                return FarmDataSourceSummary(
                    address = dataSource.address,
                    coordinate = dataSource.coordinate,
                    parcel = dataSource.parcel,
                    landCharacteristic = dataSource.landCharacteristic
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
