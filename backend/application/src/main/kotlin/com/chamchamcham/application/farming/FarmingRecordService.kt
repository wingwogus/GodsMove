package com.chamchamcham.application.farming

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordMedia
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordQueryRepository
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
class FarmingRecordService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val farmingRecordMediaRepository: FarmingRecordMediaRepository,
    private val farmingRecordQueryRepository: FarmingRecordQueryRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val plantingRecordRepository: PlantingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val fertilizingRecordRepository: FertilizingRecordRepository,
    private val pestControlRecordRepository: PestControlRecordRepository,
    private val weedingRecordRepository: WeedingRecordRepository,
    private val harvestRecordRepository: HarvestRecordRepository,
    private val detailValidator: FarmingRecordDetailValidator,
    private val cursorCodec: OpaqueCursorCodec,
) {
    fun create(command: FarmingRecordCommand.Create): FarmingRecordResult.RecordId {
        detailValidator.validate(command)
        validateImageCount(command.mediaIds)

        val member = findMember(command.memberId)
        val farm = findFarm(command.farmId, command.memberId)
        val crop = findCrop(command.cropId)
        val media = validateMedia(command.memberId, command.mediaIds)

        val record = farmingRecordRepository.save(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = command.workType,
                workedAt = command.workedAt,
                weatherCondition = command.weatherCondition,
                weatherTemperature = command.weatherTemperature,
                memo = command.memo,
                entryMode = "MANUAL",
            )
        )

        saveDetail(record, command, crop)
        attachMedia(record, media)

        return FarmingRecordResult.RecordId(id = requireNotNull(record.id), workType = record.workType)
    }

    @Transactional(readOnly = true)
    fun search(condition: FarmingRecordSearchCondition): FarmingRecordResult.Page {
        validatePageSize(condition.size)
        val cursor = decodeCursor(condition.cursor)
        val result = farmingRecordQueryRepository.search(
            FarmingRecordQueryRepository.SearchCondition(
                memberId = condition.memberId,
                cropId = condition.cropId,
                workType = condition.workType,
                workedAtFrom = condition.startDate?.atStartOfDay(),
                workedAtTo = condition.endDate?.plusDays(1)?.atStartOfDay(),
                cursor = cursor,
                size = condition.size + 1
            )
        )
        val visibleRows = result.rows.take(condition.size)
        val nextCursor = if (result.rows.size > condition.size) {
            visibleRows.lastOrNull()?.let(::encodeCursor)
        } else {
            null
        }
        return FarmingRecordResult.Page(
            items = visibleRows.map(::toSummary),
            nextCursor = nextCursor
        )
    }

    @Transactional(readOnly = true)
    fun getDetail(memberId: UUID, recordId: UUID): FarmingRecordResult.Detail {
        val record = findRecord(recordId)
        assertOwner(record, memberId)
        return toDetail(record)
    }

    fun update(command: FarmingRecordCommand.Update): FarmingRecordResult.RecordId {
        val record = findRecord(command.recordId)
        assertOwner(record, command.memberId)
        detailValidator.validate(command)
        validateImageCount(command.mediaIds)

        val farm = findFarm(command.farmId, command.memberId)
        val crop = findCrop(command.cropId)
        val media = validateMedia(command.memberId, command.mediaIds)

        val previousWorkType = record.workType
        record.update(
            farm = farm,
            crop = crop,
            workType = command.workType,
            workedAt = command.workedAt,
            weatherCondition = command.weatherCondition,
            weatherTemperature = command.weatherTemperature,
            memo = command.memo,
        )
        deleteExistingDetail(record, previousWorkType)
        saveDetail(record, command, crop)

        farmingRecordMediaRepository.deleteByRecord(record)
        attachMedia(record, media)

        return FarmingRecordResult.RecordId(id = requireNotNull(record.id), workType = record.workType)
    }

    fun delete(command: FarmingRecordCommand.Delete) {
        val record = findRecord(command.recordId)
        assertOwner(record, command.memberId)
        record.softDelete()
    }

    private fun saveDetail(record: FarmingRecord, payload: FarmingRecordDetailPayload, crop: Crop) {
        when (payload.workType) {
            WorkType.PLANTING -> payload.planting?.let { detail ->
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

            WorkType.WATERING -> payload.watering?.let { detail ->
                wateringRecordRepository.save(
                    WateringRecord(
                        record = record,
                        irrigationAmount = detail.irrigationAmount,
                        irrigationMethod = detail.irrigationMethod,
                    )
                )
            }

            WorkType.FERTILIZING -> {
                val detail = payload.fertilizing ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
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
                val detail = payload.pestControl ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
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

            WorkType.WEEDING -> payload.weeding?.let { detail ->
                weedingRecordRepository.save(WeedingRecord(record = record, weedingMethod = detail.weedingMethod))
            }

            WorkType.PRUNING -> Unit

            WorkType.HARVEST -> {
                val detail = payload.harvest ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
                harvestRecordRepository.save(
                    HarvestRecord(
                        record = record,
                        harvestAmount = detail.harvestAmount,
                        harvestAmountUnit = detail.harvestAmountUnit,
                        medicinalPart = crop.usePartCategory,
                        harvestSource = detail.harvestSource,
                        growthPeriod = detail.growthPeriod,
                        growthPeriodUnit = detail.growthPeriodUnit,
                    )
                )
            }
        }
    }

    private fun deleteExistingDetail(record: FarmingRecord, workType: WorkType) {
        when (workType) {
            WorkType.PLANTING -> plantingRecordRepository.deleteByRecord(record)
            WorkType.WATERING -> wateringRecordRepository.deleteByRecord(record)
            WorkType.FERTILIZING -> fertilizingRecordRepository.deleteByRecord(record)
            WorkType.PEST_CONTROL -> pestControlRecordRepository.deleteByRecord(record)
            WorkType.WEEDING -> weedingRecordRepository.deleteByRecord(record)
            WorkType.PRUNING -> Unit
            WorkType.HARVEST -> harvestRecordRepository.deleteByRecord(record)
        }
    }

    private fun toDetail(record: FarmingRecord): FarmingRecordResult.Detail {
        val recordId = requireNotNull(record.id) { "Persisted farming record id is required" }
        val imageUrls = farmingRecordMediaRepository.findByRecord_Id(recordId)
            .sortedBy { it.displayOrder }
            .map { it.uploadedMedia.fileUrl }

        var planting: FarmingRecordResult.PlantingDetail? = null
        var watering: FarmingRecordResult.WateringDetail? = null
        var fertilizing: FarmingRecordResult.FertilizingDetail? = null
        var pestControl: FarmingRecordResult.PestControlDetail? = null
        var weeding: FarmingRecordResult.WeedingDetail? = null
        var harvest: FarmingRecordResult.HarvestDetail? = null

        when (record.workType) {
            WorkType.PLANTING -> planting = plantingRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.PlantingDetail(
                    seedAmount = it.seedAmount,
                    seedAmountUnit = it.seedAmountUnit,
                    seedlingCount = it.seedlingCount,
                    seedlingUnit = it.seedlingUnit,
                    seedSource = it.seedSource,
                    seedPurchasePlace = it.seedPurchasePlace,
                )
            }

            WorkType.WATERING -> watering = wateringRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.WateringDetail(
                    irrigationAmount = it.irrigationAmount,
                    irrigationMethod = it.irrigationMethod,
                )
            }

            WorkType.FERTILIZING -> fertilizing = fertilizingRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.FertilizingDetail(
                    materialName = it.materialName,
                    amount = it.amount,
                    amountUnit = it.amountUnit,
                    applicationMethod = it.applicationMethod,
                )
            }

            WorkType.PEST_CONTROL -> pestControl = pestControlRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.PestControlDetail(
                    pesticideName = it.pesticideName,
                    pesticideAmount = it.pesticideAmount,
                    pesticideAmountUnit = it.pesticideAmountUnit,
                    totalSprayAmount = it.totalSprayAmount,
                    totalSprayAmountUnit = it.totalSprayAmountUnit,
                    pestTarget = it.pestTarget,
                )
            }

            WorkType.WEEDING -> weeding = weedingRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.WeedingDetail(weedingMethod = it.weedingMethod)
            }

            WorkType.PRUNING -> Unit

            WorkType.HARVEST -> harvest = harvestRecordRepository.findByRecord_Id(recordId)?.let {
                FarmingRecordResult.HarvestDetail(
                    harvestAmount = it.harvestAmount,
                    harvestAmountUnit = it.harvestAmountUnit,
                    medicinalPart = it.medicinalPart,
                    harvestSource = it.harvestSource,
                    growthPeriod = it.growthPeriod,
                    growthPeriodUnit = it.growthPeriodUnit,
                )
            }
        }

        return FarmingRecordResult.Detail(
            id = recordId,
            farmId = requireNotNull(record.farm.id) { "Persisted farm id is required" },
            farmName = record.farm.name,
            cropId = requireNotNull(record.crop.id) { "Persisted crop id is required" },
            cropName = record.crop.name,
            workType = record.workType,
            workedAt = record.workedAt,
            weatherCondition = record.weatherCondition,
            weatherTemperature = record.weatherTemperature,
            memo = record.memo,
            planting = planting,
            watering = watering,
            fertilizing = fertilizing,
            pestControl = pestControl,
            weeding = weeding,
            harvest = harvest,
            imageUrls = imageUrls,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }

    private fun toSummary(row: FarmingRecordQueryRepository.Row): FarmingRecordResult.Summary {
        val record = row.record
        return FarmingRecordResult.Summary(
            id = requireNotNull(record.id) { "Persisted farming record id is required" },
            cropId = requireNotNull(record.crop.id) { "Persisted crop id is required" },
            cropName = record.crop.name,
            workType = record.workType,
            workedAt = record.workedAt,
            weatherCondition = record.weatherCondition,
            weatherTemperature = record.weatherTemperature,
            memoPreview = record.memo.take(MEMO_PREVIEW_LENGTH),
            thumbnailUrl = row.thumbnailUrl,
        )
    }

    private fun validateImageCount(mediaIds: List<UUID>) {
        if (mediaIds.size > MAX_IMAGE_COUNT) {
            throw BusinessException(ErrorCode.FARMING_RECORD_TOO_MANY_IMAGES)
        }
    }

    private fun validateMedia(memberId: UUID, mediaIds: List<UUID>): List<UploadedMedia> {
        if (mediaIds.isEmpty()) {
            return emptyList()
        }
        val mediaById = uploadedMediaRepository.findAllById(mediaIds)
            .associateBy { requireNotNull(it.id) { "Persisted media id is required" } }

        return mediaIds.map { mediaId ->
            val media = mediaById[mediaId] ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
            if (media.owner.id != memberId) {
                throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
            }
            if (media.usageType != UploadedMediaUsageType.FARMING_RECORD) {
                throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
            }
            if (!media.isAttachable()) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            media
        }
    }

    private fun attachMedia(record: FarmingRecord, media: List<UploadedMedia>) {
        if (media.isEmpty()) {
            return
        }
        media.forEach(UploadedMedia::markAttached)
        farmingRecordMediaRepository.saveAll(
            media.mapIndexed { index, uploadedMedia ->
                FarmingRecordMedia(
                    record = record,
                    uploadedMedia = uploadedMedia,
                    displayOrder = index
                )
            }
        )
    }

    private fun validatePageSize(size: Int) {
        if (size <= 0 || size == Int.MAX_VALUE) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun decodeCursor(cursor: String?): FarmingRecordQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        val payload = cursorCodec.decode(cursor, FarmingRecordCursorPayload::class.java)
        return FarmingRecordQueryRepository.Cursor(workedAt = payload.workedAt, id = payload.id)
    }

    private fun encodeCursor(row: FarmingRecordQueryRepository.Row): String {
        val record = row.record
        return cursorCodec.encode(
            FarmingRecordCursorPayload(
                workedAt = record.workedAt,
                id = requireNotNull(record.id) { "Persisted farming record id is required" }
            )
        )
    }

    private fun findRecord(recordId: UUID): FarmingRecord =
        farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)
            ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)

    private fun assertOwner(record: FarmingRecord, memberId: UUID) {
        if (record.member.id != memberId) {
            throw BusinessException(ErrorCode.FARMING_RECORD_FORBIDDEN)
        }
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun findFarm(farmId: UUID, memberId: UUID): Farm =
        farmRepository.findByIdAndOwnerId(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

    private fun findCrop(cropId: UUID): Crop =
        cropRepository.findById(cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }

    private companion object {
        const val MAX_IMAGE_COUNT = 5
        const val MEMO_PREVIEW_LENGTH = 80
    }
}
