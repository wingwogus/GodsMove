package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository
) {
    fun getMyProfile(memberId: UUID): MemberProfileResult.MyProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwnerId(memberId)
        val crops = memberCropRepository.findByMember_Id(memberId)

        return MemberProfileResult.MyProfile(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            email = member.email,
            name = member.name,
            phone = member.phone,
            birthDate = member.birthDate,
            nickname = member.nickname,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType?.name,
            profileImageUrl = member.profileMedia?.fileUrl,
            farms = farms.map(::toMyFarm),
            crops = toCropProfiles(crops)
        )
    }

    fun getPublicProfile(memberId: UUID): MemberProfileResult.PublicProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwnerId(memberId)
        val crops = memberCropRepository.findByMember_Id(memberId)

        return MemberProfileResult.PublicProfile(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            nickname = member.nickname,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType?.name,
            profileImageUrl = member.profileMedia?.fileUrl,
            farms = farms.map(::toPublicFarm),
            crops = toCropProfiles(crops)
        )
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun toMyFarm(farm: Farm): MemberProfileResult.MyFarm =
        MemberProfileResult.MyFarm(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            name = farm.name,
            roadAddress = farm.roadAddress,
            jibunAddress = farm.jibunAddress,
            displayRegion = displayRegion(farm)
        )

    private fun toPublicFarm(farm: Farm): MemberProfileResult.PublicFarm =
        MemberProfileResult.PublicFarm(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            displayRegion = displayRegion(farm)
        )

    private fun toCropProfiles(memberCrops: List<MemberCrop>): List<MemberProfileResult.CropProfile> {
        val seen = linkedSetOf<UUID>()
        return memberCrops
            .map { it.crop }
            .filter { crop -> seen.add(requireNotNull(crop.id) { "Persisted crop id is required" }) }
            .map { crop ->
                MemberProfileResult.CropProfile(
                    cropId = requireNotNull(crop.id) { "Persisted crop id is required" },
                    cropName = crop.name
                )
            }
    }

    private fun displayRegion(farm: Farm): String? {
        val address = farm.roadAddress.takeIf { it.isNotBlank() } ?: farm.jibunAddress
        val tokens = address
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (tokens.isEmpty()) {
            return null
        }
        return tokens.take(DISPLAY_REGION_TOKEN_COUNT).joinToString(" ")
    }

    private companion object {
        const val DISPLAY_REGION_TOKEN_COUNT = 2
    }
}
