package com.chamchamcham.api.farming.dto

import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object FarmingRecordRequests {
    data class SaveRecordRequest(
        @field:NotNull(message = "농지를 선택해주세요")
        val farmId: UUID?,

        @field:NotNull(message = "작물을 선택해주세요")
        val cropId: UUID?,

        @field:NotNull(message = "작업 유형을 선택해주세요")
        val workType: WorkType?,

        @field:NotNull(message = "작업 일시를 입력해주세요")
        val workedAt: LocalDateTime?,

        @field:NotBlank(message = "날씨 상태를 입력해주세요")
        val weatherCondition: String,

        @field:NotNull(message = "기온을 입력해주세요")
        val weatherTemperature: Int?,

        @field:NotBlank(message = "메모를 입력해주세요")
        @field:Size(min = 30, max = 500, message = "메모는 30자 이상 500자 이내로 입력해주세요")
        val memo: String,

        @field:Valid
        val planting: PlantingDetailRequest? = null,

        @field:Valid
        val watering: WateringDetailRequest? = null,

        @field:Valid
        val fertilizing: FertilizingDetailRequest? = null,

        @field:Valid
        val pestControl: PestControlDetailRequest? = null,

        @field:Valid
        val weeding: WeedingDetailRequest? = null,

        @field:Valid
        val harvest: HarvestDetailRequest? = null,

        @field:Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다")
        @field:UniqueElements(message = "중복된 사진은 첨부할 수 없습니다")
        val mediaIds: List<UUID> = emptyList(),

        val entryMode: EntryMode = EntryMode.MANUAL,
    )

    data class PlantingDetailRequest(
        @field:NotNull(message = "심기 방법을 선택해주세요")
        val plantingMethod: PlantingMethod?,

        @field:DecimalMin(value = "0.01", message = "파종량은 0보다 커야 합니다")
        val seedAmount: BigDecimal? = null,
        val seedAmountUnit: SeedAmountUnit? = null,

        @field:Min(value = 1, message = "모종수는 1 이상이어야 합니다")
        val seedlingCount: Int? = null,
        val seedlingUnit: SeedlingUnit? = null,

        val propagationMethod: PropagationMethod? = null,
    )

    data class WateringDetailRequest(
        val irrigationAmount: IrrigationAmount? = null,
        val irrigationMethod: IrrigationMethod? = null,
    )

    data class FertilizingDetailRequest(
        @field:NotBlank(message = "자재명을 입력해주세요")
        val materialName: String,

        @field:DecimalMin(value = "0.01", message = "시비량은 0보다 커야 합니다")
        val amount: BigDecimal,
        val amountUnit: FertilizerAmountUnit,
        val applicationMethod: FertilizingMethod? = null,
    )

    data class PestControlDetailRequest(
        @field:NotNull(message = "농약을 선택해주세요")
        val pesticideId: UUID?,

        @field:DecimalMin(value = "0.01", message = "농약량은 0보다 커야 합니다")
        val pesticideAmount: BigDecimal,
        val pesticideAmountUnit: PesticideAmountUnit,

        @field:DecimalMin(value = "0.01", message = "살포량은 0보다 커야 합니다")
        val totalSprayAmount: BigDecimal,
        val totalSprayAmountUnit: SprayAmountUnit,
        val pestId: UUID? = null,
    )

    data class WeedingDetailRequest(
        val weedingMethod: WeedingMethod? = null,
    )

    data class HarvestDetailRequest(
        @field:DecimalMin(value = "0.01", message = "수확량은 0보다 커야 합니다")
        val harvestAmount: BigDecimal? = null,
        val harvestAmountUnknown: Boolean = false,

        val medicinalPart: CropUsePartCategory? = null,
        val harvestSource: HarvestSource = HarvestSource.CULTIVATED,

        @field:NotNull(message = "재배기간을 입력해주세요")
        @field:Min(value = 1, message = "재배기간은 1 이상이어야 합니다")
        val growthPeriod: Int?,

        @field:NotNull(message = "마지막 수확 여부를 선택해주세요")
        val isLastHarvest: Boolean?,
    )
}

fun FarmingRecordRequests.SaveRecordRequest.toCreateCommand(
    memberId: UUID,
    entryMode: EntryMode = this.entryMode,
): FarmingRecordCommand.Create =
    FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = requireNotNull(farmId),
        cropId = requireNotNull(cropId),
        workType = requireNotNull(workType),
        workedAt = requireNotNull(workedAt),
        weatherCondition = weatherCondition,
        weatherTemperature = requireNotNull(weatherTemperature),
        memo = memo,
        planting = toPlantingDetail(),
        watering = toWateringDetail(),
        fertilizing = toFertilizingDetail(),
        pestControl = toPestControlDetail(),
        weeding = toWeedingDetail(),
        harvest = toHarvestDetail(),
        mediaIds = mediaIds,
        entryMode = entryMode,
    )

fun FarmingRecordRequests.SaveRecordRequest.toPlantingDetail(): FarmingRecordCommand.PlantingDetail? = planting?.toCommand()

fun FarmingRecordRequests.SaveRecordRequest.toWateringDetail(): FarmingRecordCommand.WateringDetail? = watering?.toCommand()

fun FarmingRecordRequests.SaveRecordRequest.toFertilizingDetail(): FarmingRecordCommand.FertilizingDetail? = fertilizing?.toCommand()

fun FarmingRecordRequests.SaveRecordRequest.toPestControlDetail(): FarmingRecordCommand.PestControlDetail? = pestControl?.toCommand()

fun FarmingRecordRequests.SaveRecordRequest.toWeedingDetail(): FarmingRecordCommand.WeedingDetail? = weeding?.toCommand()

fun FarmingRecordRequests.SaveRecordRequest.toHarvestDetail(): FarmingRecordCommand.HarvestDetail? = harvest?.toCommand()

fun FarmingRecordRequests.PlantingDetailRequest.toCommand(): FarmingRecordCommand.PlantingDetail =
    FarmingRecordCommand.PlantingDetail(
        plantingMethod = requireNotNull(plantingMethod),
        seedAmount = seedAmount,
        seedAmountUnit = seedAmountUnit,
        seedlingCount = seedlingCount,
        seedlingUnit = seedlingUnit,
        propagationMethod = propagationMethod,
    )

fun FarmingRecordRequests.WateringDetailRequest.toCommand(): FarmingRecordCommand.WateringDetail =
    FarmingRecordCommand.WateringDetail(irrigationAmount = irrigationAmount, irrigationMethod = irrigationMethod)

fun FarmingRecordRequests.FertilizingDetailRequest.toCommand(): FarmingRecordCommand.FertilizingDetail =
    FarmingRecordCommand.FertilizingDetail(
        materialName = materialName,
        amount = amount,
        amountUnit = amountUnit,
        applicationMethod = applicationMethod,
    )

fun FarmingRecordRequests.PestControlDetailRequest.toCommand(): FarmingRecordCommand.PestControlDetail =
    FarmingRecordCommand.PestControlDetail(
        pesticideId = requireNotNull(pesticideId),
        pesticideAmount = pesticideAmount,
        pesticideAmountUnit = pesticideAmountUnit,
        totalSprayAmount = totalSprayAmount,
        totalSprayAmountUnit = totalSprayAmountUnit,
        pestId = pestId,
    )

fun FarmingRecordRequests.WeedingDetailRequest.toCommand(): FarmingRecordCommand.WeedingDetail =
    FarmingRecordCommand.WeedingDetail(weedingMethod = weedingMethod)

fun FarmingRecordRequests.HarvestDetailRequest.toCommand(): FarmingRecordCommand.HarvestDetail =
    FarmingRecordCommand.HarvestDetail(
        harvestAmount = harvestAmount,
        amountUnknown = harvestAmountUnknown,
        medicinalPart = medicinalPart,
        harvestSource = harvestSource,
        growthPeriod = requireNotNull(growthPeriod),
        isLastHarvest = requireNotNull(isLastHarvest),
    )
