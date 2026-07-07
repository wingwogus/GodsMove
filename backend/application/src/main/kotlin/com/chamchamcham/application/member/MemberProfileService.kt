package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmDataSource
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
    private val cropRepository: CropRepository,
    private val uploadedMediaRepository: UploadedMediaRepository
) {
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

        command.farms.forEach { farmCommand ->
            val farm = upsertFarm(member, farmCommand)
            syncFarmCrops(member, farm, farmCommand.cropIds)
        }

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
        if (command.experienceLevel !in 0..100 || command.farms.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val requestedFarmIds = command.farms.mapNotNull { it.farmId }
        if (requestedFarmIds.size != requestedFarmIds.toSet().size) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        command.farms.forEach { farm ->
            if (farm.name.isBlank() || farm.roadAddress.isBlank() || farm.latitude == null || farm.longitude == null) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
            if (farm.areaSqm != null && farm.areaSqm.signum() <= 0) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
            if (farm.cropIds.isEmpty()) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
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

    private fun upsertFarm(member: Member, command: MemberProfileCommand.Farm): Farm {
        val boundaryCoordinates = command.boundaryCoordinates.map {
            FarmBoundaryCoordinate(latitude = it.latitude, longitude = it.longitude)
        }
        val dataSource = FarmDataSource(
            address = command.dataSource.address,
            coordinate = command.dataSource.coordinate,
            parcel = command.dataSource.parcel,
            landCharacteristic = command.dataSource.landCharacteristic
        )

        if (command.farmId == null) {
            return farmRepository.save(
                Farm(
                    owner = member,
                    name = command.name,
                    roadAddress = command.roadAddress,
                    jibunAddress = command.jibunAddress,
                    latitude = command.latitude,
                    longitude = command.longitude,
                    pnu = command.pnu,
                    landCategory = command.landCategory,
                    areaSqm = command.areaSqm,
                    areaIsManualEntry = command.areaIsManualEntry,
                    boundaryCoordinates = boundaryCoordinates.toMutableList(),
                    dataSource = dataSource
                )
            )
        }

        val farm = farmRepository.findById(command.farmId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        if (farm.owner.id != member.id) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        farm.updateProfile(
            name = command.name,
            roadAddress = command.roadAddress,
            jibunAddress = command.jibunAddress,
            latitude = command.latitude,
            longitude = command.longitude,
            pnu = command.pnu,
            landCategory = command.landCategory,
            areaSqm = command.areaSqm,
            areaIsManualEntry = command.areaIsManualEntry,
            boundaryCoordinates = boundaryCoordinates,
            dataSource = dataSource
        )
        return farm
    }

    private fun syncFarmCrops(member: Member, farm: Farm, cropIds: List<UUID>) {
        val distinctCropIds = cropIds.distinct()
        val crops = loadCrops(distinctCropIds)
        val farmId = requireNotNull(farm.id) { "Persisted farm id is required" }
        val memberId = requireNotNull(member.id) { "Persisted member id is required" }

        memberCropRepository.deleteByMember_IdAndFarm_Id(memberId, farmId)
        memberCropRepository.saveAll(
            crops.map { crop ->
                MemberCrop(
                    member = member,
                    farm = farm,
                    crop = crop
                )
            }
        )
    }

    private fun loadCrops(cropIds: List<UUID>): List<Crop> {
        val cropsById = cropRepository.findAllById(cropIds)
            .associateBy { requireNotNull(it.id) { "Persisted crop id is required" } }
        if (cropsById.size != cropIds.size) {
            throw BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        return cropIds.map { cropsById.getValue(it) }
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
