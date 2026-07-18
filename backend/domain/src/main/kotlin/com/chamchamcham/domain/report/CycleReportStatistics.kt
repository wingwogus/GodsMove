package com.chamchamcham.domain.report

import com.chamchamcham.domain.farming.WorkType
import java.math.BigDecimal
import java.time.LocalDate

data class Coverage(val recordedCount: Int, val targetCount: Int)

data class CountDistribution(
    val code: String,
    val label: String,
    val count: Int,
    val ratePct: BigDecimal,
)

data class AmountByUnit(
    val unit: String,
    val amount: BigDecimal,
    val coverage: Coverage,
)

data class PropagationStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val totalQuantity: BigDecimal?,
    val quantityUnit: String?,
    val quantityCoverage: Coverage,
)

data class MaterialCategoryStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val amountKg: BigDecimal,
    val amountRatePct: BigDecimal,
)

data class CategoryMethodStatistics(
    val categoryCode: String,
    val categoryLabel: String,
    val methodCode: String,
    val methodLabel: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
)

data class CategoryAmountByUnit(
    val categoryCode: String,
    val categoryLabel: String,
    val unit: String,
    val recordCount: Int,
    val amount: BigDecimal,
    val coverage: Coverage,
)

data class TargetCount(val target: String, val count: Int)

data class HarvestPartStatistics(
    val code: String,
    val label: String,
    val recordCount: Int,
    val recordRatePct: BigDecimal,
    val knownAmountKg: BigDecimal?,
    val amountRatePct: BigDecimal?,
    val amountCoverage: Coverage,
)

data class GrowthPeriodRange(val minMonths: Int, val maxMonths: Int)

data class CommonOnlyStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
) {
    companion object {
        fun empty() = CommonOnlyStatistics()
    }
}

data class PlantingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val plantingMethodDistribution: List<CountDistribution> = emptyList(),
    val propagationMethods: List<PropagationStatistics> = emptyList(),
) {
    companion object {
        fun empty() = PlantingStatistics()
    }
}

data class WateringStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val amountDistribution: List<CountDistribution> = emptyList(),
    val methodDistribution: List<CountDistribution> = emptyList(),
) {
    companion object {
        fun empty() = WateringStatistics()
    }
}

data class FertilizingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val totalAmountKg: BigDecimal? = null,
    val averageAmountKg: BigDecimal? = null,
    val amountCoverage: Coverage = Coverage(0, 0),
    val materialDistribution: List<CountDistribution> = emptyList(),
    val materialCategories: List<MaterialCategoryStatistics> = emptyList(),
    val methodDistribution: List<CountDistribution> = emptyList(),
    val categoryMethods: List<CategoryMethodStatistics> = emptyList(),
) {
    companion object {
        fun empty() = FertilizingStatistics()
    }
}

data class PestControlStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val categoryDistribution: List<CountDistribution> = emptyList(),
    val pesticideAmounts: List<AmountByUnit> = emptyList(),
    val categoryAmounts: List<CategoryAmountByUnit> = emptyList(),
    val totalSprayAmountLiters: BigDecimal? = null,
    val sprayAmountCoverage: Coverage = Coverage(0, 0),
    val targets: List<TargetCount> = emptyList(),
) {
    companion object {
        fun empty() = PestControlStatistics()
    }
}

data class WeedingStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val methodDistribution: List<CountDistribution> = emptyList(),
) {
    companion object {
        fun empty() = WeedingStatistics()
    }
}

data class HarvestStatistics(
    val recordCount: Int = 0,
    val firstWorkedOn: LocalDate? = null,
    val lastWorkedOn: LocalDate? = null,
    val workedDayCount: Int = 0,
    val averageIntervalDays: BigDecimal? = null,
    val photoAttachedRecordCount: Int = 0,
    val photoAttachmentRatePct: BigDecimal? = null,
    val weatherDistribution: List<CountDistribution> = emptyList(),
    val averageTemperatureC: BigDecimal? = null,
    val totalAmountKg: BigDecimal? = null,
    val averageAmountKg: BigDecimal? = null,
    val amountCoverage: Coverage = Coverage(0, 0),
    val firstHarvestedOn: LocalDate? = null,
    val lastHarvestedOn: LocalDate? = null,
    val medicinalParts: List<HarvestPartStatistics> = emptyList(),
    val finalGrowthPeriodMonths: Int? = null,
    val growthPeriodRangeMonths: GrowthPeriodRange? = null,
    val growthPeriodDistribution: List<CountDistribution> = emptyList(),
) {
    companion object {
        fun empty() = HarvestStatistics()
    }
}

data class CycleReportStatistics(
    val planting: PlantingStatistics = PlantingStatistics.empty(),
    val watering: WateringStatistics = WateringStatistics.empty(),
    val fertilizing: FertilizingStatistics = FertilizingStatistics.empty(),
    val pestControl: PestControlStatistics = PestControlStatistics.empty(),
    val weeding: WeedingStatistics = WeedingStatistics.empty(),
    val pruning: CommonOnlyStatistics = CommonOnlyStatistics.empty(),
    val harvest: HarvestStatistics = HarvestStatistics.empty(),
    val etc: CommonOnlyStatistics = CommonOnlyStatistics.empty(),
) {
    fun recordCountFor(workType: WorkType): Int = when (workType) {
        WorkType.PLANTING -> planting.recordCount
        WorkType.WATERING -> watering.recordCount
        WorkType.FERTILIZING -> fertilizing.recordCount
        WorkType.PEST_CONTROL -> pestControl.recordCount
        WorkType.WEEDING -> weeding.recordCount
        WorkType.PRUNING -> pruning.recordCount
        WorkType.HARVEST -> harvest.recordCount
        WorkType.ETC -> etc.recordCount
    }

    fun lastWorkedOnFor(workType: WorkType): LocalDate? = when (workType) {
        WorkType.PLANTING -> planting.lastWorkedOn
        WorkType.WATERING -> watering.lastWorkedOn
        WorkType.FERTILIZING -> fertilizing.lastWorkedOn
        WorkType.PEST_CONTROL -> pestControl.lastWorkedOn
        WorkType.WEEDING -> weeding.lastWorkedOn
        WorkType.PRUNING -> pruning.lastWorkedOn
        WorkType.HARVEST -> harvest.lastWorkedOn
        WorkType.ETC -> etc.lastWorkedOn
    }

    companion object {
        fun empty() = CycleReportStatistics()
    }
}
