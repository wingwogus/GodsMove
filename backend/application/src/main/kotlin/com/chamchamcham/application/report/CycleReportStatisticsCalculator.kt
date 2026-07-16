package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
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
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class CycleReportStatisticsCalculator {
    fun calculate(records: List<CycleReportSourceRecord>): CycleReportStatistics {
        val grouped = records.groupBy { it.workType }
        return CycleReportStatistics(
            planting = calculatePlanting(grouped[WorkType.PLANTING].orEmpty()),
            watering = calculateWatering(grouped[WorkType.WATERING].orEmpty()),
            fertilizing = calculateFertilizing(grouped[WorkType.FERTILIZING].orEmpty()),
            pestControl = calculatePestControl(grouped[WorkType.PEST_CONTROL].orEmpty()),
            weeding = calculateWeeding(grouped[WorkType.WEEDING].orEmpty()),
            pruning = commonOnly(grouped[WorkType.PRUNING].orEmpty()),
            harvest = calculateHarvest(grouped[WorkType.HARVEST].orEmpty()),
            etc = commonOnly(grouped[WorkType.ETC].orEmpty()),
        )
    }

    private fun calculatePlanting(records: List<CycleReportSourceRecord>): PlantingStatistics {
        val common = common(records)
        val details = records.mapNotNull { it.planting }
        return PlantingStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            propagationMethods = details
                .groupBy { it.propagationMethod ?: it.plantingMethod }
                .map { (method, items) ->
                    val knownQuantities = items.mapNotNull { it.quantity }
                    PropagationStatistics(
                        code = method.code,
                        label = method.label,
                        recordCount = items.size,
                        recordRatePct = requireNotNull(StatisticsMath.percentage(items.size, records.size)),
                        totalQuantity = knownQuantities.sumOrNullScale4(),
                        quantityUnit = items.mapNotNull { it.quantityUnit }.distinct().singleOrNull(),
                        quantityCoverage = Coverage(knownQuantities.size, items.size),
                    )
                }
                .sortedWith(compareByDescending<PropagationStatistics> { it.recordCount }.thenBy { it.code }),
        )
    }

    private fun calculateWatering(records: List<CycleReportSourceRecord>): WateringStatistics {
        val common = common(records)
        return WateringStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            amountDistribution = distribution(records.map { it.watering?.amount }, records.size),
            methodDistribution = distribution(records.map { it.watering?.method }, records.size),
        )
    }

    private fun calculateFertilizing(records: List<CycleReportSourceRecord>): FertilizingStatistics {
        val common = common(records)
        val details = records.mapNotNull { it.fertilizing }
        val knownAmounts = details.mapNotNull { it.amountKg }
        val totalAmount = knownAmounts.sumOrNullScale4()
        return FertilizingStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            totalAmountKg = totalAmount,
            averageAmountKg = StatisticsMath.average(knownAmounts),
            amountCoverage = Coverage(knownAmounts.size, records.size),
            materialDistribution = distribution(
                details.map { CategoryRef(it.materialName, it.materialName) },
                records.size,
            ),
            materialCategories = calculateMaterialCategories(details, records.size, totalAmount),
            methodDistribution = distribution(records.map { it.fertilizing?.applicationMethod }, records.size),
            categoryMethods = calculateCategoryMethods(details),
        )
    }

    private fun calculatePestControl(records: List<CycleReportSourceRecord>): PestControlStatistics {
        val common = common(records)
        val details = records.mapNotNull { it.pestControl }
        val knownSprayAmounts = details.mapNotNull { it.totalSprayAmountLiters }
        return PestControlStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            categoryDistribution = distribution(
                details.map { CategoryRef(it.pesticideId.toString(), it.pesticideName) },
                records.size,
            ),
            pesticideAmounts = details
                .groupBy { it.pesticideAmountUnit }
                .map { (unit, items) ->
                    val knownAmounts = items.mapNotNull { it.pesticideAmount }
                    AmountByUnit(
                        unit = unit,
                        amount = knownAmounts.sumScale4(),
                        coverage = Coverage(knownAmounts.size, items.size),
                    )
                }
                .sortedBy { it.unit },
            categoryAmounts = details
                .groupBy { CategoryRef(it.pesticideId.toString(), it.pesticideName) to it.pesticideAmountUnit }
                .map { (key, items) ->
                    val (category, unit) = key
                    val knownAmounts = items.mapNotNull { it.pesticideAmount }
                    CategoryAmountByUnit(
                        categoryCode = category.code,
                        categoryLabel = category.label,
                        unit = unit,
                        recordCount = items.size,
                        amount = knownAmounts.sumScale4(),
                        coverage = Coverage(knownAmounts.size, items.size),
                    )
                }
                .sortedWith(compareBy<CategoryAmountByUnit> { it.categoryCode }.thenBy { it.unit }),
            totalSprayAmountLiters = knownSprayAmounts.sumOrNullScale4(),
            sprayAmountCoverage = Coverage(knownSprayAmounts.size, records.size),
            targets = details
                .mapNotNull { it.pestName?.trim()?.takeIf(String::isNotEmpty) }
                .groupingBy { it }
                .eachCount()
                .map { (target, count) -> TargetCount(target, count) }
                .sortedWith(compareByDescending<TargetCount> { it.count }.thenBy { it.target }),
        )
    }

    private fun calculateWeeding(records: List<CycleReportSourceRecord>): WeedingStatistics {
        val common = common(records)
        return WeedingStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            methodDistribution = distribution(records.map { it.weeding?.method }, records.size),
        )
    }

    private fun calculateHarvest(records: List<CycleReportSourceRecord>): HarvestStatistics {
        val common = common(records)
        val details = records.mapNotNull { it.harvest }
        val knownAmounts = details.mapNotNull { it.amountKg }
        val totalAmount = knownAmounts.sumOrNullScale4()
        val harvestDates = records.map { it.workedAt.toLocalDate() }
        return HarvestStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
            totalAmountKg = totalAmount,
            averageAmountKg = StatisticsMath.average(knownAmounts),
            amountCoverage = Coverage(knownAmounts.size, records.size),
            firstHarvestedOn = harvestDates.minOrNull(),
            lastHarvestedOn = harvestDates.maxOrNull(),
            medicinalParts = calculateHarvestParts(details, records.size, totalAmount),
            finalGrowthPeriodMonths = records
                .sortedWith(compareBy(CycleReportSourceRecord::workedAt, CycleReportSourceRecord::id))
                .mapNotNull { it.harvest }
                .lastOrNull { it.isLastHarvest }
                ?.growthPeriodMonths,
            growthPeriodRangeMonths = details
                .mapNotNull { it.growthPeriodMonths }
                .takeIf { it.isNotEmpty() }
                ?.let { GrowthPeriodRange(requireNotNull(it.minOrNull()), requireNotNull(it.maxOrNull())) },
        )
    }

    private fun commonOnly(records: List<CycleReportSourceRecord>): CommonOnlyStatistics {
        val common = common(records)
        return CommonOnlyStatistics(
            recordCount = common.recordCount,
            firstWorkedOn = common.firstWorkedOn,
            lastWorkedOn = common.lastWorkedOn,
            workedDayCount = common.workedDayCount,
            averageIntervalDays = common.averageIntervalDays,
            photoAttachedRecordCount = common.photoAttachedRecordCount,
            photoAttachmentRatePct = common.photoAttachmentRatePct,
            weatherDistribution = common.weatherDistribution,
            averageTemperatureC = common.averageTemperatureC,
        )
    }

    private fun calculateMaterialCategories(
        details: List<FertilizingReportSource>,
        recordCount: Int,
        totalAmount: BigDecimal?,
    ): List<MaterialCategoryStatistics> {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0) return emptyList()
        return details
            .filter { it.amountKg != null }
            .groupBy { it.materialName }
            .map { (materialName, items) ->
                val amount = items.mapNotNull { it.amountKg }.sumScale4()
                MaterialCategoryStatistics(
                    code = materialName,
                    label = materialName,
                    recordCount = items.size,
                    recordRatePct = requireNotNull(StatisticsMath.percentage(items.size, recordCount)),
                    amountKg = amount,
                    amountRatePct = requireNotNull(StatisticsMath.percentage(amount, totalAmount)),
                )
            }
            .sortedWith(compareByDescending<MaterialCategoryStatistics> { it.recordCount }.thenBy { it.code })
    }

    private fun calculateCategoryMethods(
        details: List<FertilizingReportSource>,
    ): List<CategoryMethodStatistics> =
        details
            .groupBy { it.materialName }
            .toSortedMap()
            .flatMap { (materialName, materialItems) ->
                materialItems
                    .map { it.applicationMethod ?: CategoryRef("MISSING", "미입력") }
                    .groupingBy { it }
                    .eachCount()
                    .map { (method, count) ->
                        CategoryMethodStatistics(
                            categoryCode = materialName,
                            categoryLabel = materialName,
                            methodCode = method.code,
                            methodLabel = method.label,
                            recordCount = count,
                            recordRatePct = requireNotNull(StatisticsMath.percentage(count, materialItems.size)),
                        )
                    }
                    .sortedBy { it.methodCode }
            }

    private fun calculateHarvestParts(
        details: List<HarvestReportSource>,
        recordCount: Int,
        totalAmount: BigDecimal?,
    ): List<HarvestPartStatistics> =
        details
            .mapNotNull { detail -> detail.medicinalPart?.let { it to detail } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (part, items) ->
                val knownAmounts = items.mapNotNull { it.amountKg }
                val partAmount = knownAmounts.sumOrNullScale4()
                HarvestPartStatistics(
                    code = part.code,
                    label = part.label,
                    recordCount = items.size,
                    recordRatePct = requireNotNull(StatisticsMath.percentage(items.size, recordCount)),
                    knownAmountKg = partAmount,
                    amountRatePct = partAmount?.let { amount ->
                        totalAmount?.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }?.let {
                            StatisticsMath.percentage(amount, it)
                        }
                    },
                    amountCoverage = Coverage(knownAmounts.size, items.size),
                )
            }
            .sortedWith(compareByDescending<HarvestPartStatistics> { it.recordCount }.thenBy { it.code })

    private fun common(records: List<CycleReportSourceRecord>): CommonValues {
        val dates = records.map { it.workedAt.toLocalDate() }
        val photoCount = records.count { it.hasPhoto }
        return CommonValues(
            recordCount = records.size,
            firstWorkedOn = dates.minOrNull(),
            lastWorkedOn = dates.maxOrNull(),
            workedDayCount = dates.distinct().size,
            averageIntervalDays = StatisticsMath.averageIntervalDays(dates),
            photoAttachedRecordCount = photoCount,
            photoAttachmentRatePct = StatisticsMath.percentage(photoCount, records.size),
            weatherDistribution = distribution(
                values = records.map {
                    it.weatherCondition.trim().takeIf(String::isNotEmpty)
                        ?.let { value -> CategoryRef(value, value) }
                },
                denominator = records.size,
            ),
            averageTemperatureC = StatisticsMath.average(
                records.map { BigDecimal(it.weatherTemperature) },
            ),
        )
    }

    private fun distribution(values: List<CategoryRef?>, denominator: Int): List<CountDistribution> =
        values.map { it ?: CategoryRef("MISSING", "미입력") }
            .groupingBy { it }
            .eachCount()
            .map { (item, count) ->
                CountDistribution(
                    code = item.code,
                    label = item.label,
                    count = count,
                    ratePct = requireNotNull(StatisticsMath.percentage(count, denominator)),
                )
            }
            .sortedWith(compareByDescending<CountDistribution> { it.count }.thenBy { it.code })

    private fun List<BigDecimal>.sumScale4(): BigDecimal =
        fold(BigDecimal.ZERO) { total, value -> total + value }.setScale(4, RoundingMode.HALF_UP)

    private fun List<BigDecimal>.sumOrNullScale4(): BigDecimal? =
        takeIf { it.isNotEmpty() }?.sumScale4()

    private data class CommonValues(
        val recordCount: Int,
        val firstWorkedOn: LocalDate?,
        val lastWorkedOn: LocalDate?,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachedRecordCount: Int,
        val photoAttachmentRatePct: BigDecimal?,
        val weatherDistribution: List<CountDistribution>,
        val averageTemperatureC: BigDecimal?,
    )
}
