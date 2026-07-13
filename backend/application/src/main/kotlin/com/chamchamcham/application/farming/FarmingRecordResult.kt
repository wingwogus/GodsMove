package com.chamchamcham.application.farming

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object FarmingRecordResult {
    data class RecordId(
        val id: UUID,
        val workType: WorkType,
    )

    data class Summary(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memoPreview: String,
        val thumbnailUrl: String?,
    )

    data class Page(
        val items: List<Summary>,
        val nextCursor: String?,
    )

    data class Detail(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memo: String,
        val planting: PlantingDetail? = null,
        val watering: WateringDetail? = null,
        val fertilizing: FertilizingDetail? = null,
        val pestControl: PestControlDetail? = null,
        val weeding: WeedingDetail? = null,
        val harvest: HarvestDetail? = null,
        val imageUrls: List<String>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    )

    data class PlantingDetail(
        val seedAmount: BigDecimal?,
        val seedAmountUnit: SeedAmountUnit?,
        val seedlingCount: Int?,
        val seedlingUnit: SeedlingUnit?,
        val propagationMethod: PropagationMethod,
    )

    data class WateringDetail(
        val irrigationAmount: IrrigationAmount?,
        val irrigationMethod: IrrigationMethod?,
    )

    data class FertilizingDetail(
        val materialName: String,
        val amount: BigDecimal,
        val amountUnit: FertilizerAmountUnit,
        val applicationMethod: FertilizingMethod?,
    )

    data class PestControlDetail(
        val pesticideId: UUID,
        val pesticideName: String,
        val pesticideAmount: BigDecimal,
        val pesticideAmountUnit: PesticideAmountUnit,
        val totalSprayAmount: BigDecimal,
        val totalSprayAmountUnit: SprayAmountUnit,
        val pestId: UUID?,
        val pestName: String?,
    )

    data class WeedingDetail(
        val weedingMethod: WeedingMethod?,
    )

    data class HarvestDetail(
        val harvestAmount: BigDecimal?,
        val medicinalPart: CropUsePartCategory,
        val harvestSource: HarvestSource,
        val growthPeriod: Int,
        val growthPeriodUnit: GrowthPeriodUnit,
        val isLastHarvest: Boolean,
    )
}
