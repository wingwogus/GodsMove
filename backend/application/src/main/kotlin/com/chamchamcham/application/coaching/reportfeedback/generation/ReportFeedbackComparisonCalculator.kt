package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.WateringStatistics
import com.chamchamcham.domain.report.WeedingStatistics
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

data class ReportFeedbackComparison(
    val metricKey: String,
    val metricLabel: String,
    val currentValue: BigDecimal,
    val previousValue: BigDecimal,
    val difference: BigDecimal,
    val relativeChangePct: BigDecimal?,
    val unit: String,
    val currentCoverage: Coverage? = null,
    val previousCoverage: Coverage? = null,
)

@Component
class ReportFeedbackComparisonCalculator {
    fun calculate(
        workType: WorkType,
        current: CycleReportStatistics,
        previous: CycleReportStatistics,
    ): List<ReportFeedbackComparison> = buildList {
        val currentCommon = current.commonFor(workType)
        val previousCommon = previous.commonFor(workType)
        addComparison(
            metricKey = "recordCount",
            metricLabel = "기록 횟수",
            currentValue = currentCommon.recordCount.toBigDecimal(),
            previousValue = previousCommon.recordCount.toBigDecimal(),
            unit = "회",
        )
        addComparison(
            metricKey = "workedDayCount",
            metricLabel = "작업한 날짜 수",
            currentValue = currentCommon.workedDayCount.toBigDecimal(),
            previousValue = previousCommon.workedDayCount.toBigDecimal(),
            unit = "일",
        )
        addComparison(
            metricKey = "averageIntervalDays",
            metricLabel = "평균 작업 간격",
            currentValue = currentCommon.averageIntervalDays,
            previousValue = previousCommon.averageIntervalDays,
            unit = "일",
        )
        addComparison(
            metricKey = "photoAttachmentRatePct",
            metricLabel = "사진 첨부 비율",
            currentValue = currentCommon.photoAttachmentRatePct,
            previousValue = previousCommon.photoAttachmentRatePct,
            unit = "%",
        )

        when (workType) {
            WorkType.PLANTING -> {
                val previousByKey = previous.planting.propagationMethods.associateBy {
                    it.code to it.quantityUnit
                }
                current.planting.propagationMethods
                    .sortedWith(compareBy({ it.code }, { it.quantityUnit }))
                    .forEach { currentQuantity ->
                        val unit = currentQuantity.quantityUnit ?: return@forEach
                        val previousQuantity = previousByKey[currentQuantity.code to unit] ?: return@forEach
                        addComparison(
                            metricKey = "planting.quantity.${currentQuantity.code}.$unit",
                            metricLabel = "${currentQuantity.label} 심은 양",
                            currentValue = currentQuantity.totalQuantity,
                            previousValue = previousQuantity.totalQuantity,
                            unit = unit,
                            currentCoverage = currentQuantity.quantityCoverage,
                            previousCoverage = previousQuantity.quantityCoverage,
                        )
                    }
            }

            WorkType.FERTILIZING -> addComparison(
                metricKey = "fertilizing.totalAmountKg",
                metricLabel = "거름 총량",
                currentValue = current.fertilizing.totalAmountKg,
                previousValue = previous.fertilizing.totalAmountKg,
                unit = "KG",
                currentCoverage = current.fertilizing.amountCoverage,
                previousCoverage = previous.fertilizing.amountCoverage,
            )

            WorkType.PEST_CONTROL -> {
                val previousByUnit = previous.pestControl.pesticideAmounts.associateBy { it.unit }
                current.pestControl.pesticideAmounts
                    .sortedBy { it.unit }
                    .forEach { currentAmount ->
                        val previousAmount = previousByUnit[currentAmount.unit] ?: return@forEach
                        addComparison(
                            metricKey = "pestControl.pesticideAmount.${currentAmount.unit}",
                            metricLabel = "약 사용량",
                            currentValue = currentAmount.amount,
                            previousValue = previousAmount.amount,
                            unit = currentAmount.unit,
                            currentCoverage = currentAmount.coverage,
                            previousCoverage = previousAmount.coverage,
                        )
                    }
                addComparison(
                    metricKey = "pestControl.totalSprayAmountLiters",
                    metricLabel = "약을 섞은 물의 총량",
                    currentValue = current.pestControl.totalSprayAmountLiters,
                    previousValue = previous.pestControl.totalSprayAmountLiters,
                    unit = "L",
                    currentCoverage = current.pestControl.sprayAmountCoverage,
                    previousCoverage = previous.pestControl.sprayAmountCoverage,
                )
            }

            WorkType.HARVEST -> addComparison(
                metricKey = "harvest.totalAmountKg",
                metricLabel = "총수확량",
                currentValue = current.harvest.totalAmountKg,
                previousValue = previous.harvest.totalAmountKg,
                unit = "KG",
                currentCoverage = current.harvest.amountCoverage,
                previousCoverage = previous.harvest.amountCoverage,
            )

            WorkType.WATERING,
            WorkType.WEEDING,
            WorkType.PRUNING,
            WorkType.ETC,
            -> Unit
        }
    }

    private fun MutableList<ReportFeedbackComparison>.addComparison(
        metricKey: String,
        metricLabel: String,
        currentValue: BigDecimal?,
        previousValue: BigDecimal?,
        unit: String,
        currentCoverage: Coverage? = null,
        previousCoverage: Coverage? = null,
    ) {
        if (currentValue == null || previousValue == null) return
        val difference = currentValue.subtract(previousValue)
        add(
            ReportFeedbackComparison(
                metricKey = metricKey,
                metricLabel = metricLabel,
                currentValue = currentValue.normalized(),
                previousValue = previousValue.normalized(),
                difference = difference.normalized(),
                relativeChangePct = if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
                    null
                } else {
                    difference
                        .multiply(BigDecimal(100))
                        .divide(previousValue, RELATIVE_SCALE, RoundingMode.HALF_UP)
                        .normalized()
                },
                unit = unit,
                currentCoverage = currentCoverage,
                previousCoverage = previousCoverage,
            ),
        )
    }

    private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros()

    private fun CycleReportStatistics.commonFor(workType: WorkType): CommonComparableStatistics =
        when (workType) {
            WorkType.PLANTING -> planting.toCommon()
            WorkType.WATERING -> watering.toCommon()
            WorkType.FERTILIZING -> fertilizing.toCommon()
            WorkType.PEST_CONTROL -> pestControl.toCommon()
            WorkType.WEEDING -> weeding.toCommon()
            WorkType.PRUNING -> pruning.toCommon()
            WorkType.HARVEST -> harvest.toCommon()
            WorkType.ETC -> etc.toCommon()
        }

    private fun PlantingStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun WateringStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun FertilizingStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun PestControlStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun WeedingStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun CommonOnlyStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private fun HarvestStatistics.toCommon() = CommonComparableStatistics(
        recordCount, workedDayCount, averageIntervalDays, photoAttachmentRatePct,
    )

    private data class CommonComparableStatistics(
        val recordCount: Int,
        val workedDayCount: Int,
        val averageIntervalDays: BigDecimal?,
        val photoAttachmentRatePct: BigDecimal?,
    )

    private companion object {
        const val RELATIVE_SCALE = 2
    }
}
