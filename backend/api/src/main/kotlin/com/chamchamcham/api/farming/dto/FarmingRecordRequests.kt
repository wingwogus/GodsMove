package com.chamchamcham.api.farming.dto

import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedSource
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
        val mediaIds: List<UUID> = emptyList()
    )

    data class PlantingDetailRequest(
        @field:DecimalMin(value = "0.01", message = "파종량은 0보다 커야 합니다")
        val seedAmount: BigDecimal? = null,
        val seedAmountUnit: SeedAmountUnit? = null,

        @field:Min(value = 1, message = "모종수는 1 이상이어야 합니다")
        val seedlingCount: Int? = null,
        val seedlingUnit: SeedlingUnit? = null,
        val seedSource: SeedSource? = null,
        val seedPurchasePlace: String? = null,
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
        @field:NotBlank(message = "농약명을 입력해주세요")
        val pesticideName: String,

        @field:DecimalMin(value = "0.01", message = "농약량은 0보다 커야 합니다")
        val pesticideAmount: BigDecimal,
        val pesticideAmountUnit: PesticideAmountUnit,

        @field:DecimalMin(value = "0.01", message = "살포량은 0보다 커야 합니다")
        val totalSprayAmount: BigDecimal,
        val totalSprayAmountUnit: SprayAmountUnit,
        val pestTarget: String? = null,
    )

    data class WeedingDetailRequest(
        val weedingMethod: WeedingMethod? = null,
    )

    data class HarvestDetailRequest(
        @field:DecimalMin(value = "0.01", message = "수확량은 0보다 커야 합니다")
        val harvestAmount: BigDecimal,
        val harvestAmountUnit: HarvestAmountUnit,
        val harvestSource: HarvestSource = HarvestSource.CULTIVATED,

        @field:Min(value = 1, message = "재배기간은 1 이상이어야 합니다")
        val growthPeriod: Int,
        val growthPeriodUnit: GrowthPeriodUnit,
    )
}
