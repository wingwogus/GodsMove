package com.chamchamcham.api.report.dto

import com.chamchamcham.domain.report.AmountByUnit
import com.chamchamcham.domain.report.CategoryAmountByUnit
import com.chamchamcham.domain.report.CategoryMethodStatistics
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.CountDistribution
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.GrowthPeriodRange
import com.chamchamcham.domain.report.HarvestPartStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.MaterialCategoryStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.PropagationStatistics
import com.chamchamcham.domain.report.TargetCount
import com.chamchamcham.domain.report.WateringStatistics
import com.chamchamcham.domain.report.WeedingStatistics
import java.math.BigDecimal
import java.time.LocalDate

object FarmingReportStatisticsResponses {
    data class CoverageResponse(
        val recordedCount: Int,
        val targetCount: Int,
    ) {
        companion object {
            fun from(source: Coverage) = CoverageResponse(
                recordedCount = source.recordedCount,
                targetCount = source.targetCount,
            )
        }
    }

    data class CountDistributionResponse(
        val code: String,
        val label: String,
        val count: Int,
        val ratePct: BigDecimal,
    ) {
        companion object {
            fun from(source: CountDistribution) = CountDistributionResponse(
                code = source.code,
                label = source.label,
                count = source.count,
                ratePct = source.ratePct,
            )
        }
    }

    data class AmountByUnitResponse(
        val unit: String,
        val amount: BigDecimal,
        val coverage: CoverageResponse,
    ) {
        companion object {
            fun from(source: AmountByUnit) = AmountByUnitResponse(
                unit = source.unit,
                amount = source.amount,
                coverage = CoverageResponse.from(source.coverage),
            )
        }
    }

    data class PropagationStatisticsResponse(
        val code: String,
        val label: String,
        val recordCount: Int,
        val recordRatePct: BigDecimal,
        val totalQuantity: BigDecimal?,
        val quantityUnit: String?,
        val quantityCoverage: CoverageResponse,
    ) {
        companion object {
            fun from(source: PropagationStatistics) = PropagationStatisticsResponse(
                code = source.code,
                label = source.label,
                recordCount = source.recordCount,
                recordRatePct = source.recordRatePct,
                totalQuantity = source.totalQuantity,
                quantityUnit = source.quantityUnit,
                quantityCoverage = CoverageResponse.from(source.quantityCoverage),
            )
        }
    }

    data class MaterialCategoryStatisticsResponse(
        val code: String,
        val label: String,
        val recordCount: Int,
        val recordRatePct: BigDecimal,
        val amountKg: BigDecimal,
        val amountRatePct: BigDecimal,
    ) {
        companion object {
            fun from(source: MaterialCategoryStatistics) = MaterialCategoryStatisticsResponse(
                code = source.code,
                label = source.label,
                recordCount = source.recordCount,
                recordRatePct = source.recordRatePct,
                amountKg = source.amountKg,
                amountRatePct = source.amountRatePct,
            )
        }
    }

    data class CategoryMethodStatisticsResponse(
        val categoryCode: String,
        val categoryLabel: String,
        val methodCode: String,
        val methodLabel: String,
        val recordCount: Int,
        val recordRatePct: BigDecimal,
    ) {
        companion object {
            fun from(source: CategoryMethodStatistics) = CategoryMethodStatisticsResponse(
                categoryCode = source.categoryCode,
                categoryLabel = source.categoryLabel,
                methodCode = source.methodCode,
                methodLabel = source.methodLabel,
                recordCount = source.recordCount,
                recordRatePct = source.recordRatePct,
            )
        }
    }

    data class CategoryAmountByUnitResponse(
        val categoryCode: String,
        val categoryLabel: String,
        val unit: String,
        val recordCount: Int,
        val amount: BigDecimal,
        val coverage: CoverageResponse,
    ) {
        companion object {
            fun from(source: CategoryAmountByUnit) = CategoryAmountByUnitResponse(
                categoryCode = source.categoryCode,
                categoryLabel = source.categoryLabel,
                unit = source.unit,
                recordCount = source.recordCount,
                amount = source.amount,
                coverage = CoverageResponse.from(source.coverage),
            )
        }
    }

    data class TargetCountResponse(
        val target: String,
        val count: Int,
    ) {
        companion object {
            fun from(source: TargetCount) = TargetCountResponse(
                target = source.target,
                count = source.count,
            )
        }
    }

    data class HarvestPartStatisticsResponse(
        val code: String,
        val label: String,
        val recordCount: Int,
        val recordRatePct: BigDecimal,
        val knownAmountKg: BigDecimal?,
        val amountRatePct: BigDecimal?,
        val amountCoverage: CoverageResponse,
    ) {
        companion object {
            fun from(source: HarvestPartStatistics) = HarvestPartStatisticsResponse(
                code = source.code,
                label = source.label,
                recordCount = source.recordCount,
                recordRatePct = source.recordRatePct,
                knownAmountKg = source.knownAmountKg,
                amountRatePct = source.amountRatePct,
                amountCoverage = CoverageResponse.from(source.amountCoverage),
            )
        }
    }

    data class GrowthPeriodRangeResponse(
        val minMonths: Int,
        val maxMonths: Int,
    ) {
        companion object {
            fun from(source: GrowthPeriodRange) = GrowthPeriodRangeResponse(
                minMonths = source.minMonths,
                maxMonths = source.maxMonths,
            )
        }
    }

    data class CommonStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
    ) {
        companion object {
            fun from(source: CommonOnlyStatistics) = CommonStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
            )
        }
    }

    data class PlantingStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val plantingMethodDistribution: List<CountDistributionResponse>,
        val propagationMethods: List<PropagationStatisticsResponse>,
    ) {
        companion object {
            fun from(source: PlantingStatistics) = PlantingStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                plantingMethodDistribution = source.plantingMethodDistribution.map(CountDistributionResponse::from),
                propagationMethods = source.propagationMethods.map(PropagationStatisticsResponse::from),
            )
        }
    }

    data class WateringStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val amountDistribution: List<CountDistributionResponse>,
        val methodDistribution: List<CountDistributionResponse>,
    ) {
        companion object {
            fun from(source: WateringStatistics) = WateringStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                amountDistribution = source.amountDistribution.map(CountDistributionResponse::from),
                methodDistribution = source.methodDistribution.map(CountDistributionResponse::from),
            )
        }
    }

    data class FertilizingStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val totalAmountKg: BigDecimal?,
        val averageAmountKg: BigDecimal?,
        val amountCoverage: CoverageResponse,
        val materialCategories: List<MaterialCategoryStatisticsResponse>,
        val methodDistribution: List<CountDistributionResponse>,
        val categoryMethods: List<CategoryMethodStatisticsResponse>,
    ) {
        companion object {
            fun from(source: FertilizingStatistics) = FertilizingStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                totalAmountKg = source.totalAmountKg,
                averageAmountKg = source.averageAmountKg,
                amountCoverage = CoverageResponse.from(source.amountCoverage),
                materialCategories = source.materialCategories.map(MaterialCategoryStatisticsResponse::from),
                methodDistribution = source.methodDistribution.map(CountDistributionResponse::from),
                categoryMethods = source.categoryMethods.map(CategoryMethodStatisticsResponse::from),
            )
        }
    }

    data class PestControlStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val categoryDistribution: List<CountDistributionResponse>,
        val pesticideAmounts: List<AmountByUnitResponse>,
        val categoryAmounts: List<CategoryAmountByUnitResponse>,
        val totalSprayAmountMl: BigDecimal?,
        val sprayAmountCoverage: CoverageResponse,
        val targets: List<TargetCountResponse>,
    ) {
        companion object {
            fun from(source: PestControlStatistics) = PestControlStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                categoryDistribution = source.categoryDistribution.map(CountDistributionResponse::from),
                pesticideAmounts = source.pesticideAmounts.map(AmountByUnitResponse::from),
                categoryAmounts = source.categoryAmounts.map(CategoryAmountByUnitResponse::from),
                totalSprayAmountMl = source.totalSprayAmountLiters
                    ?.movePointRight(3)
                    ?.setScale(4),
                sprayAmountCoverage = CoverageResponse.from(source.sprayAmountCoverage),
                targets = source.targets.map(TargetCountResponse::from),
            )
        }
    }

    data class WeedingStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val methodDistribution: List<CountDistributionResponse>,
    ) {
        companion object {
            fun from(source: WeedingStatistics) = WeedingStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                methodDistribution = source.methodDistribution.map(CountDistributionResponse::from),
            )
        }
    }

    data class HarvestStatisticsResponse(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistributionResponse>,
        val averageTemperatureC: BigDecimal?,
        val totalAmountKg: BigDecimal?,
        val averageAmountKg: BigDecimal?,
        val amountCoverage: CoverageResponse,
        val firstHarvestedOn: LocalDate?,
        val lastHarvestedOn: LocalDate?,
        val medicinalParts: List<HarvestPartStatisticsResponse>,
        val finalGrowthPeriodMonths: Int?,
        val growthPeriodRangeMonths: GrowthPeriodRangeResponse?,
        val growthPeriodDistribution: List<CountDistributionResponse>,
    ) {
        companion object {
            fun from(source: HarvestStatistics) = HarvestStatisticsResponse(
                recordCount = source.recordCount,
                firstWorkedOn = source.firstWorkedOn,
                lastWorkedOn = source.lastWorkedOn,
                workedDayCount = source.workedDayCount,
                averageIntervalDays = source.averageIntervalDays,
                photoAttachedRecordCount = source.photoAttachedRecordCount,
                photoAttachmentRatePct = source.photoAttachmentRatePct,
                weatherDistribution = source.weatherDistribution.map(CountDistributionResponse::from),
                averageTemperatureC = source.averageTemperatureC,
                totalAmountKg = source.totalAmountKg,
                averageAmountKg = source.averageAmountKg,
                amountCoverage = CoverageResponse.from(source.amountCoverage),
                firstHarvestedOn = source.firstHarvestedOn,
                lastHarvestedOn = source.lastHarvestedOn,
                medicinalParts = source.medicinalParts.map(HarvestPartStatisticsResponse::from),
                finalGrowthPeriodMonths = source.finalGrowthPeriodMonths,
                growthPeriodRangeMonths = source.growthPeriodRangeMonths?.let(GrowthPeriodRangeResponse::from),
                growthPeriodDistribution = source.growthPeriodDistribution.map(CountDistributionResponse::from),
            )
        }
    }

    data class CycleStatisticsResponse(
        val planting: PlantingStatisticsResponse,
        val watering: WateringStatisticsResponse,
        val fertilizing: FertilizingStatisticsResponse,
        val pestControl: PestControlStatisticsResponse,
        val weeding: WeedingStatisticsResponse,
        val pruning: CommonStatisticsResponse,
        val harvest: HarvestStatisticsResponse,
        val etc: CommonStatisticsResponse,
    ) {
        companion object {
            fun from(source: CycleReportStatistics) = CycleStatisticsResponse(
                planting = PlantingStatisticsResponse.from(source.planting),
                watering = WateringStatisticsResponse.from(source.watering),
                fertilizing = FertilizingStatisticsResponse.from(source.fertilizing),
                pestControl = PestControlStatisticsResponse.from(source.pestControl),
                weeding = WeedingStatisticsResponse.from(source.weeding),
                pruning = CommonStatisticsResponse.from(source.pruning),
                harvest = HarvestStatisticsResponse.from(source.harvest),
                etc = CommonStatisticsResponse.from(source.etc),
            )
        }
    }
}
