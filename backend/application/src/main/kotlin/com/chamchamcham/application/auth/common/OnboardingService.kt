package com.chamchamcham.application.auth.common

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmDataSource
import com.chamchamcham.domain.farm.FarmRepository
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
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun complete(command: AuthCommand.CompleteOnboarding): AuthResult.OnboardingComplete {
        if (command.cropIds.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val requestedCropIds = command.cropIds.distinct()
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
            nickname = command.nickname,
            experienceLevel = command.experienceLevel,
            managementType = command.managementType
        )
        val farm = farmRepository.save(
            Farm(
                owner = member,
                name = command.farm.name,
                roadAddress = command.farm.roadAddress,
                jibunAddress = command.farm.jibunAddress,
                latitude = command.farm.latitude,
                longitude = command.farm.longitude,
                pnu = command.farm.pnu,
                landCategory = command.farm.landCategory,
                areaSqm = command.farm.areaSqm,
                areaIsManualEntry = command.farm.areaIsManualEntry,
                boundaryCoordinates = command.farm.boundaryCoordinates.map {
                    FarmBoundaryCoordinate(latitude = it.latitude, longitude = it.longitude)
                }.toMutableList(),
                dataSource = FarmDataSource(
                    address = command.farm.dataSource.address,
                    coordinate = command.farm.dataSource.coordinate,
                    parcel = command.farm.dataSource.parcel,
                    landCharacteristic = command.farm.dataSource.landCharacteristic
                )
            )
        )
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
