package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.FertilizingRecord
import com.chamchamcham.domain.farming.FertilizingRecordRepository
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.PestControlRecord
import com.chamchamcham.domain.farming.PestControlRecordRepository
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.PlantingRecordRepository
import com.chamchamcham.domain.farming.WateringRecord
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WeedingRecord
import com.chamchamcham.domain.farming.WeedingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class FarmingRecordService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val plantingRecordRepository: PlantingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val fertilizingRecordRepository: FertilizingRecordRepository,
    private val pestControlRecordRepository: PestControlRecordRepository,
    private val weedingRecordRepository: WeedingRecordRepository,
    private val harvestRecordRepository: HarvestRecordRepository,
    private val detailValidator: FarmingRecordDetailValidator,
) {
    fun create(command: FarmingRecordCommand.Create): FarmingRecordResult.Detail {
        detailValidator.validate(command)

        val member = findMember(command.memberId)
        val farm = findFarm(command.farmId, command.memberId)
        val crop = findCrop(command.cropId)

        val record = farmingRecordRepository.save(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = command.workType,
                workedAt = command.workedAt,
                memo = command.memo,
                entryMode = "MANUAL",
            )
        )

        saveDetail(record, command)

        return FarmingRecordResult.Detail(id = requireNotNull(record.id), workType = record.workType)
    }

    private fun saveDetail(record: FarmingRecord, command: FarmingRecordCommand.Create) {
        when (command.workType) {
            WorkType.PLANTING -> command.planting?.let { detail ->
                plantingRecordRepository.save(
                    PlantingRecord(
                        record = record,
                        seedAmount = detail.seedAmount,
                        seedAmountUnit = detail.seedAmountUnit,
                        seedlingCount = detail.seedlingCount,
                        seedlingUnit = detail.seedlingUnit,
                        seedSource = detail.seedSource,
                        seedPurchasePlace = detail.seedPurchasePlace,
                    )
                )
            }

            WorkType.WATERING -> command.watering?.let { detail ->
                wateringRecordRepository.save(
                    WateringRecord(
                        record = record,
                        irrigationAmount = detail.irrigationAmount,
                        irrigationMethod = detail.irrigationMethod,
                    )
                )
            }

            WorkType.FERTILIZING -> {
                val detail = command.fertilizing ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
                fertilizingRecordRepository.save(
                    FertilizingRecord(
                        record = record,
                        materialName = detail.materialName,
                        amount = detail.amount,
                        amountUnit = detail.amountUnit,
                        applicationMethod = detail.applicationMethod,
                    )
                )
            }

            WorkType.PEST_CONTROL -> {
                val detail = command.pestControl ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
                pestControlRecordRepository.save(
                    PestControlRecord(
                        record = record,
                        pesticideName = detail.pesticideName,
                        pesticideAmount = detail.pesticideAmount,
                        pesticideAmountUnit = detail.pesticideAmountUnit,
                        totalSprayAmount = detail.totalSprayAmount,
                        totalSprayAmountUnit = detail.totalSprayAmountUnit,
                        pestTarget = detail.pestTarget,
                    )
                )
            }

            WorkType.WEEDING -> command.weeding?.let { detail ->
                weedingRecordRepository.save(WeedingRecord(record = record, weedingMethod = detail.weedingMethod))
            }

            WorkType.PRUNING -> Unit

            WorkType.HARVEST -> {
                val detail = command.harvest ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
                harvestRecordRepository.save(
                    HarvestRecord(
                        record = record,
                        harvestAmount = detail.harvestAmount,
                        harvestAmountUnit = detail.harvestAmountUnit,
                        medicinalPart = record.crop.usePartCategory,
                        harvestSource = detail.harvestSource,
                        growthPeriod = detail.growthPeriod,
                        growthPeriodUnit = detail.growthPeriodUnit,
                    )
                )
            }
        }
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun findFarm(farmId: UUID, memberId: UUID): Farm =
        farmRepository.findByIdAndOwner_Id(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

    private fun findCrop(cropId: UUID): Crop =
        cropRepository.findById(cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
}
