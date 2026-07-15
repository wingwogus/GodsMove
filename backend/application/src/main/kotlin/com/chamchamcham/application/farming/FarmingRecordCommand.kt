package com.chamchamcham.application.farming

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
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object FarmingRecordCommand {
    data class Create(
        val memberId: UUID,
        val farmId: UUID,
        val cropId: UUID,
        override val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memo: String,
        override val planting: PlantingDetail? = null,
        override val watering: WateringDetail? = null,
        override val fertilizing: FertilizingDetail? = null,
        override val pestControl: PestControlDetail? = null,
        override val weeding: WeedingDetail? = null,
        override val harvest: HarvestDetail? = null,
        val mediaIds: List<UUID> = emptyList(),
        val entryMode: EntryMode = EntryMode.MANUAL,
    ) : FarmingRecordDetailPayload

    data class Update(
        val memberId: UUID,
        val recordId: UUID,
        val farmId: UUID,
        val cropId: UUID,
        override val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memo: String,
        override val planting: PlantingDetail? = null,
        override val watering: WateringDetail? = null,
        override val fertilizing: FertilizingDetail? = null,
        override val pestControl: PestControlDetail? = null,
        override val weeding: WeedingDetail? = null,
        override val harvest: HarvestDetail? = null,
        val mediaIds: List<UUID> = emptyList(),
    ) : FarmingRecordDetailPayload

    data class Delete(
        val memberId: UUID,
        val recordId: UUID,
    )

    data class PlantingDetail(
        val plantingMethod: PlantingMethod,
        val seedAmount: BigDecimal? = null,
        val seedAmountUnit: SeedAmountUnit? = null,
        val seedlingCount: Int? = null,
        val seedlingUnit: SeedlingUnit? = null,
        val propagationMethod: PropagationMethod? = null,
    )

    data class WateringDetail(
        val irrigationAmount: IrrigationAmount? = null,
        val irrigationMethod: IrrigationMethod? = null,
    )

    data class FertilizingDetail(
        val materialName: String,
        val amount: BigDecimal,
        val amountUnit: FertilizerAmountUnit,
        val applicationMethod: FertilizingMethod? = null,
    )

    data class PestControlDetail(
        val pesticideId: UUID,
        val pesticideAmount: BigDecimal,
        val pesticideAmountUnit: PesticideAmountUnit,
        val totalSprayAmount: BigDecimal,
        val totalSprayAmountUnit: SprayAmountUnit,
        val pestId: UUID? = null,
    )

    data class WeedingDetail(
        val weedingMethod: WeedingMethod? = null,
    )

    data class HarvestDetail(
        val harvestAmount: BigDecimal?,
        val amountUnknown: Boolean = false,
        val medicinalPart: CropUsePartCategory? = null,
        val harvestSource: HarvestSource = HarvestSource.CULTIVATED,
        val growthPeriod: Int,
        val isLastHarvest: Boolean,
    )
}
