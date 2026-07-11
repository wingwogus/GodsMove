package com.chamchamcham.application.member

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository,
    private val uploadedMediaRepository: UploadedMediaRepository
) {
    @Transactional(readOnly = true)
    fun getMyProfile(memberId: UUID): MemberProfileResult.MyProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwnerId(memberId)
        val crops = memberCropRepository.findByMemberId(memberId)

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

    @Transactional(readOnly = true)
    fun getPublicProfile(memberId: UUID): MemberProfileResult.PublicProfile {
        val member = findMember(memberId)
        val farms = farmRepository.findByOwnerId(memberId)
        val crops = memberCropRepository.findByMemberId(memberId)

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

    @Transactional(readOnly = true)
    fun getMyFarmCrops(memberId: UUID): List<MemberProfileResult.FarmCrops> {
        val farms = farmRepository.findByOwnerId(memberId)
        val cropsByFarmId = memberCropRepository.findByMemberId(memberId).groupBy { it.farm.id }

        return farms.map { farm ->
            val farmId = requireNotNull(farm.id) { "Persisted farm id is required" }
            MemberProfileResult.FarmCrops(
                farmId = farmId,
                farmName = farm.name,
                crops = cropsByFarmId[farmId].orEmpty().map { CropResult.CropSummary.from(it.crop) }
            )
        }
    }

    fun updateMyProfile(command: MemberProfileCommand.UpdateMyProfile): MemberProfileResult.MyProfile {
        validateUpdateCommand(command)
        val member = findMember(command.memberId)

        member.completeOnboarding(
            name = command.name,
            phone = command.phone,
            birthDate = command.birthDate,
            nickname = command.nickname,
            experienceLevel = command.experienceLevel,
            managementType = command.managementType
        )
        syncProfileImage(member, command.profileMediaId)

        return getMyProfile(command.memberId)
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun validateUpdateCommand(command: MemberProfileCommand.UpdateMyProfile) {
        if (command.name.isBlank() || command.phone.isBlank() || command.nickname.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        if (command.experienceLevel !in 0..100) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun syncProfileImage(member: Member, profileMediaId: UUID?) {
        val currentMedia = member.profileMedia
        if (profileMediaId == null) {
            currentMedia?.markDeleted()
            member.updateProfileMedia(null)
            return
        }
        if (currentMedia?.id == profileMediaId) {
            return
        }

        val newMedia = uploadedMediaRepository.findById(profileMediaId).orElseThrow {
            BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        }
        validateProfileMedia(member, newMedia)
        currentMedia?.markDeleted()
        newMedia.markAttached()
        member.updateProfileMedia(newMedia)
    }

    private fun validateProfileMedia(member: Member, media: UploadedMedia) {
        if (media.owner.id != member.id) {
            throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
        }
        if (media.usageType != UploadedMediaUsageType.PROFILE) {
            throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
        }
        if (!media.isAttachable()) {
            throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
        }
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
