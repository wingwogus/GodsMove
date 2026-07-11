package com.chamchamcham.application.member

import com.chamchamcham.application.crop.CropResult
import java.time.LocalDate
import java.util.UUID

object MemberProfileResult {
    data class MyProfile(
        val memberId: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<MyFarm>,
        val crops: List<CropProfile>
    )

    data class PublicProfile(
        val memberId: UUID,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<PublicFarm>,
        val crops: List<CropProfile>
    )

    data class MyFarm(
        val farmId: UUID,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val displayRegion: String?
    )

    data class PublicFarm(
        val farmId: UUID,
        val displayRegion: String?
    )

    data class CropProfile(
        val cropId: UUID,
        val cropName: String
    )

    data class FarmCrops(
        val farmId: UUID,
        val farmName: String,
        val crops: List<CropResult.CropSummary>
    )
}
