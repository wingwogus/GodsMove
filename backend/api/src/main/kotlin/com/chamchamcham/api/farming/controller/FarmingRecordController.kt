package com.chamchamcham.api.farming.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.farming.dto.FarmingRecordRequests
import com.chamchamcham.api.farming.dto.FarmingRecordResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.farming.WorkType
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/farming-records")
class FarmingRecordController(
    private val farmingRecordService: FarmingRecordService
) {
    @PostMapping
    fun createRecord(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: FarmingRecordRequests.SaveRecordRequest
    ): ResponseEntity<ApiResponse<FarmingRecordResponses.RecordIdResponse>> {
        val result = farmingRecordService.create(toCreateCommand(parseMemberId(memberId), request))
        return ResponseEntity.ok(ApiResponse.ok(FarmingRecordResponses.RecordIdResponse.from(result)))
    }

    @GetMapping
    fun listRecords(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) cropId: UUID?,
        @RequestParam(required = false) workType: WorkType?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<FarmingRecordResponses.RecordPageResponse>> {
        val page = farmingRecordService.search(
            FarmingRecordSearchCondition(
                memberId = parseMemberId(memberId),
                cropId = cropId,
                workType = workType,
                startDate = startDate,
                endDate = endDate,
                keyword = keyword,
                cursor = cursor,
                size = size
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(FarmingRecordResponses.RecordPageResponse.from(page)))
    }

    @GetMapping("/{recordId}")
    fun getRecord(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable recordId: UUID
    ): ResponseEntity<ApiResponse<FarmingRecordResponses.RecordDetailResponse>> {
        val detail = farmingRecordService.getDetail(parseMemberId(memberId), recordId)
        return ResponseEntity.ok(ApiResponse.ok(FarmingRecordResponses.RecordDetailResponse.from(detail)))
    }

    @PatchMapping("/{recordId}")
    fun updateRecord(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable recordId: UUID,
        @Valid @RequestBody request: FarmingRecordRequests.SaveRecordRequest
    ): ResponseEntity<ApiResponse<FarmingRecordResponses.RecordIdResponse>> {
        val result = farmingRecordService.update(toUpdateCommand(parseMemberId(memberId), recordId, request))
        return ResponseEntity.ok(ApiResponse.ok(FarmingRecordResponses.RecordIdResponse.from(result)))
    }

    @DeleteMapping("/{recordId}")
    fun deleteRecord(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable recordId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        farmingRecordService.delete(FarmingRecordCommand.Delete(memberId = parseMemberId(memberId), recordId = recordId))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    private fun toCreateCommand(memberId: UUID, request: FarmingRecordRequests.SaveRecordRequest): FarmingRecordCommand.Create =
        FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = requireNotNull(request.farmId),
            cropId = requireNotNull(request.cropId),
            workType = requireNotNull(request.workType),
            workedAt = requireNotNull(request.workedAt),
            weatherCondition = request.weatherCondition,
            weatherTemperature = requireNotNull(request.weatherTemperature),
            memo = request.memo,
            planting = request.toPlantingDetail(),
            watering = request.toWateringDetail(),
            fertilizing = request.toFertilizingDetail(),
            pestControl = request.toPestControlDetail(),
            weeding = request.toWeedingDetail(),
            harvest = request.toHarvestDetail(),
            mediaIds = request.mediaIds,
        )

    private fun toUpdateCommand(
        memberId: UUID,
        recordId: UUID,
        request: FarmingRecordRequests.SaveRecordRequest
    ): FarmingRecordCommand.Update =
        FarmingRecordCommand.Update(
            memberId = memberId,
            recordId = recordId,
            farmId = requireNotNull(request.farmId),
            cropId = requireNotNull(request.cropId),
            workType = requireNotNull(request.workType),
            workedAt = requireNotNull(request.workedAt),
            weatherCondition = request.weatherCondition,
            weatherTemperature = requireNotNull(request.weatherTemperature),
            memo = request.memo,
            planting = request.toPlantingDetail(),
            watering = request.toWateringDetail(),
            fertilizing = request.toFertilizingDetail(),
            pestControl = request.toPestControlDetail(),
            weeding = request.toWeedingDetail(),
            harvest = request.toHarvestDetail(),
            mediaIds = request.mediaIds,
        )

    private fun FarmingRecordRequests.SaveRecordRequest.toPlantingDetail(): FarmingRecordCommand.PlantingDetail? =
        planting?.let {
            FarmingRecordCommand.PlantingDetail(
                seedAmount = it.seedAmount,
                seedAmountUnit = it.seedAmountUnit,
                seedlingCount = it.seedlingCount,
                seedlingUnit = it.seedlingUnit,
                propagationMethod = requireNotNull(it.propagationMethod),
            )
        }

    private fun FarmingRecordRequests.SaveRecordRequest.toWateringDetail(): FarmingRecordCommand.WateringDetail? =
        watering?.let {
            FarmingRecordCommand.WateringDetail(
                irrigationAmount = it.irrigationAmount,
                irrigationMethod = it.irrigationMethod,
            )
        }

    private fun FarmingRecordRequests.SaveRecordRequest.toFertilizingDetail(): FarmingRecordCommand.FertilizingDetail? =
        fertilizing?.let {
            FarmingRecordCommand.FertilizingDetail(
                materialName = it.materialName,
                amount = it.amount,
                amountUnit = it.amountUnit,
                applicationMethod = it.applicationMethod,
            )
        }

    private fun FarmingRecordRequests.SaveRecordRequest.toPestControlDetail(): FarmingRecordCommand.PestControlDetail? =
        pestControl?.let {
            FarmingRecordCommand.PestControlDetail(
                pesticideName = it.pesticideName,
                pesticideAmount = it.pesticideAmount,
                pesticideAmountUnit = it.pesticideAmountUnit,
                totalSprayAmount = it.totalSprayAmount,
                totalSprayAmountUnit = it.totalSprayAmountUnit,
                pestTarget = it.pestTarget,
            )
        }

    private fun FarmingRecordRequests.SaveRecordRequest.toWeedingDetail(): FarmingRecordCommand.WeedingDetail? =
        weeding?.let { FarmingRecordCommand.WeedingDetail(weedingMethod = it.weedingMethod) }

    private fun FarmingRecordRequests.SaveRecordRequest.toHarvestDetail(): FarmingRecordCommand.HarvestDetail? =
        harvest?.let {
            FarmingRecordCommand.HarvestDetail(
                harvestAmount = it.harvestAmount,
                amountUnknown = it.harvestAmountUnknown,
                medicinalPart = requireNotNull(it.medicinalPart),
                harvestSource = it.harvestSource,
                growthPeriod = it.growthPeriod,
                growthPeriodUnit = it.growthPeriodUnit,
            )
        }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
