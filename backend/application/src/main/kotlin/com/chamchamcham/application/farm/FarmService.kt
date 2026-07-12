package com.chamchamcham.application.farm

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class FarmService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmingRecordRepository: FarmingRecordRepository
) {
    @Transactional(readOnly = true)
    fun list(memberId: UUID): List<FarmResult.Detail> {
        val cropsByFarmId = memberCropRepository.findAllWithCropByMemberId(memberId)
            .groupBy { requireNotNull(it.farm.id) { "Persisted farm id is required" } }

        return farmRepository.findAllWithBoundaryCoordinatesByOwnerId(memberId).map { farm ->
            val crops = cropsByFarmId[requireNotNull(farm.id) { "Persisted farm id is required" }]
                .orEmpty()
                .map { CropResult.CropSummary.from(it.crop) }
            FarmResult.Detail.from(farm, crops)
        }
    }

    fun create(command: FarmCommand.Create): FarmResult.Detail {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val crops = loadCrops(command.cropIds)
        val farm = farmRepository.save(command.draft.toFarm(member))
        memberCropRepository.saveAll(
            crops.map { crop ->
                MemberCrop(member = member, farm = farm, crop = crop)
            }
        )
        return FarmResult.Detail.from(farm, crops.map(CropResult.CropSummary::from))
    }

    fun replace(command: FarmCommand.Replace): FarmResult.Detail {
        val farm = findOwnedFarm(command.memberId, command.farmId)
        val requestedCrops = loadCrops(command.cropIds)
        val currentLinks = memberCropRepository.findAllWithCropByMemberIdAndFarmId(command.memberId, command.farmId)
        val requestedCropIds = requestedCrops.map { requireNotNull(it.id) { "Persisted crop id is required" } }.toSet()
        val removedLinks = currentLinks.filter { requireNotNull(it.crop.id) !in requestedCropIds }

        assertRemovable(command.memberId, command.farmId, removedLinks)
        farm.apply(command.draft)
        if (removedLinks.isNotEmpty()) {
            memberCropRepository.deleteAll(removedLinks)
        }

        val currentCropIds = currentLinks.map { requireNotNull(it.crop.id) }.toSet()
        val addedLinks = requestedCrops
            .filter { requireNotNull(it.id) !in currentCropIds }
            .map { crop ->
                MemberCrop(member = farm.owner, farm = farm, crop = crop)
            }
        if (addedLinks.isNotEmpty()) {
            memberCropRepository.saveAll(addedLinks)
        }

        return FarmResult.Detail.from(farm, requestedCrops.map(CropResult.CropSummary::from))
    }

    fun delete(command: FarmCommand.Delete) {
        val farm = findOwnedFarm(command.memberId, command.farmId)
        if (farmingRecordRepository.existsByFarm_Id(command.farmId)) {
            throw BusinessException(ErrorCode.FARM_IN_USE)
        }
        memberCropRepository.deleteByMemberIdAndFarmId(command.memberId, command.farmId)
        farmRepository.delete(farm)
    }

    private fun findOwnedFarm(memberId: UUID, farmId: UUID): Farm =
        farmRepository.findByIdAndOwnerId(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

    private fun loadCrops(cropIds: List<UUID>): List<Crop> {
        val cropsById = cropRepository.findAllById(cropIds)
            .associateBy { requireNotNull(it.id) { "Persisted crop id is required" } }
        if (cropsById.size != cropIds.size) {
            throw BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        return cropIds.map(cropsById::getValue)
    }

    private fun assertRemovable(memberId: UUID, farmId: UUID, links: List<MemberCrop>) {
        links.forEach { link ->
            if (farmingRecordRepository.existsByMember_IdAndFarm_IdAndCrop_Id(memberId, farmId, requireNotNull(link.crop.id))) {
                throw BusinessException(ErrorCode.FARM_CROP_IN_USE)
            }
        }
    }
}
