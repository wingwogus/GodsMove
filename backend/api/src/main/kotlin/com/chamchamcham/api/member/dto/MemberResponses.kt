package com.chamchamcham.api.member.dto

import com.chamchamcham.api.crop.dto.CropResponses
import com.chamchamcham.application.member.MemberProfileResult
import java.time.LocalDate
import java.util.UUID

object MemberResponses {
    data class MyProfileResponse(
        val memberId: UUID,
        val email: String?,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<MyFarmResponse>,
        val crops: List<CropProfileResponse>
    ) {
        companion object {
            fun from(result: MemberProfileResult.MyProfile): MyProfileResponse =
                MyProfileResponse(
                    memberId = result.memberId,
                    email = result.email,
                    name = result.name,
                    phone = result.phone,
                    birthDate = result.birthDate,
                    nickname = result.nickname,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType,
                    profileImageUrl = result.profileImageUrl,
                    farms = result.farms.map(MyFarmResponse::from),
                    crops = result.crops.map(CropProfileResponse::from)
                )
        }
    }

    data class PublicProfileResponse(
        val memberId: UUID,
        val nickname: String?,
        val experienceLevel: Int?,
        val managementType: String?,
        val profileImageUrl: String?,
        val farms: List<PublicFarmResponse>,
        val crops: List<CropProfileResponse>
    ) {
        companion object {
            fun from(result: MemberProfileResult.PublicProfile): PublicProfileResponse =
                PublicProfileResponse(
                    memberId = result.memberId,
                    nickname = result.nickname,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType,
                    profileImageUrl = result.profileImageUrl,
                    farms = result.farms.map(PublicFarmResponse::from),
                    crops = result.crops.map(CropProfileResponse::from)
                )
        }
    }

    data class MyFarmResponse(
        val farmId: UUID,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val displayRegion: String?
    ) {
        companion object {
            fun from(result: MemberProfileResult.MyFarm): MyFarmResponse =
                MyFarmResponse(
                    farmId = result.farmId,
                    name = result.name,
                    roadAddress = result.roadAddress,
                    jibunAddress = result.jibunAddress,
                    displayRegion = result.displayRegion
                )
        }
    }

    data class PublicFarmResponse(
        val farmId: UUID,
        val displayRegion: String?
    ) {
        companion object {
            fun from(result: MemberProfileResult.PublicFarm): PublicFarmResponse =
                PublicFarmResponse(
                    farmId = result.farmId,
                    displayRegion = result.displayRegion
                )
        }
    }

    data class CropProfileResponse(
        val cropId: UUID,
        val cropName: String
    ) {
        companion object {
            fun from(result: MemberProfileResult.CropProfile): CropProfileResponse =
                CropProfileResponse(
                    cropId = result.cropId,
                    cropName = result.cropName
                )
        }
    }

    data class FarmCropsResponse(
        val farmId: UUID,
        val farmName: String,
        val crops: List<CropResponses.CropResponse>
    ) {
        companion object {
            fun from(result: MemberProfileResult.FarmCrops): FarmCropsResponse =
                FarmCropsResponse(
                    farmId = result.farmId,
                    farmName = result.farmName,
                    crops = result.crops.map(CropResponses.CropResponse::from)
                )
        }
    }
}
