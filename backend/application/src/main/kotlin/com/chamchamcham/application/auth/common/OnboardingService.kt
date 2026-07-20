package com.chamchamcham.application.auth.common

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farm.toFarm
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OnboardingService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val memberCropRepository: MemberCropRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun complete(command: AuthCommand.CompleteOnboarding): AuthResult.OnboardingComplete {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val profileMedia = command.profileMediaId?.let { mediaId ->
            val media = uploadedMediaRepository.findById(mediaId).orElseThrow {
                BusinessException(ErrorCode.MEDIA_NOT_FOUND)
            }
            if (media.owner.id != member.id) {
                throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
            }
            if (media.usageType != UploadedMediaUsageType.PROFILE) {
                throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
            }
            if (!media.isAttachable()) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            media
        }
        val requestedCropIds = command.cropIds
        val cropsById = cropRepository.findAllById(requestedCropIds)
            .associateBy { requireNotNull(it.id) { "Persisted crop id is required" } }

        if (cropsById.size != requestedCropIds.size) {
            throw BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        val crops = requestedCropIds.map { cropsById.getValue(it) }

        member.completeOnboarding(
            name = command.name,
            phone = command.phone,
            birthDate = command.birthDate,
            nickname = command.nickname?.trim()?.takeUnless { it.isBlank() }
                ?: command.name?.trim()?.takeUnless { it.isBlank() },
            experienceLevel = command.experienceLevel,
            managementType = command.managementType
        )
        profileMedia?.let {
            member.updateProfileMedia(it)
            it.markAttached()
        }
        val farm = farmRepository.save(command.farm.toFarm(member))
        memberCropRepository.saveAll(
            crops.map { crop ->
                MemberCrop(
                    member = member,
                    farm = farm,
                    crop = crop
                )
            }
        )

        return AuthResult.OnboardingComplete(
            member = AuthResult.MemberProfile.from(member),
            farm = AuthResult.FarmSummary.from(farm),
            crops = crops.map(CropResult.CropSummary::from),
            onboarding = onboardingStatusResolver.resolve(member)
        )
    }
}
