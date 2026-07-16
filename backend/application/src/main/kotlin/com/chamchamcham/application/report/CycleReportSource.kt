package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ReportScope(
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
)

data class CategoryRef(val code: String, val label: String)

data class PlantingReportSource(
    val plantingMethod: CategoryRef,
    val propagationMethod: CategoryRef?,
    val quantity: BigDecimal?,
    val quantityUnit: String?,
)

data class WateringReportSource(
    val amount: CategoryRef?,
    val method: CategoryRef?,
)

data class FertilizingReportSource(
    val materialName: String,
    val amount: BigDecimal,
    val amountUnit: String,
    val amountKg: BigDecimal?,
    val applicationMethod: CategoryRef?,
)

data class PestControlReportSource(
    val pesticideId: UUID,
    val pesticideName: String,
    val pesticideAmount: BigDecimal?,
    val pesticideAmountUnit: String,
    val totalSprayAmountLiters: BigDecimal?,
    val pestName: String?,
)

data class WeedingReportSource(val method: CategoryRef?)

data class HarvestReportSource(
    val amountKg: BigDecimal?,
    val medicinalPart: CategoryRef?,
    val harvestSource: CategoryRef = CategoryRef("CULTIVATED", "재배"),
    val growthPeriod: Int? = null,
    val growthPeriodUnit: String? = null,
    val growthPeriodMonths: Int?,
    val isLastHarvest: Boolean,
)

data class CycleReportSourceRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    val createdAt: LocalDateTime = workedAt,
    val workType: WorkType,
    val weatherCondition: String,
    val weatherTemperature: Int,
    val hasPhoto: Boolean,
    val memo: String = "",
    val planting: PlantingReportSource? = null,
    val watering: WateringReportSource? = null,
    val fertilizing: FertilizingReportSource? = null,
    val pestControl: PestControlReportSource? = null,
    val weeding: WeedingReportSource? = null,
    val harvest: HarvestReportSource? = null,
)

data class CycleSlice(
    val status: FarmingCycleReportStatus,
    val startBasis: FarmingCycleStartBasis,
    val records: List<CycleReportSourceRecord>,
) {
    val finalHarvestRecordId: UUID? = records.lastOrNull()
        ?.takeIf { it.harvest?.isLastHarvest == true }
        ?.id
}
