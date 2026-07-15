package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.AmountByUnit
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.PropagationStatistics
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ReportFeedbackComparisonCalculatorTest {
    private val calculator = ReportFeedbackComparisonCalculator()

    @Test
    fun `common metrics use stable order and deterministic differences`() {
        val current = CycleReportStatistics(
            watering = WateringStatistics(
                recordCount = 6,
                workedDayCount = 5,
                averageIntervalDays = decimal("2.5"),
                photoAttachmentRatePct = decimal("75.0"),
            ),
        )
        val previous = CycleReportStatistics(
            watering = WateringStatistics(
                recordCount = 4,
                workedDayCount = 4,
                averageIntervalDays = decimal("4.0"),
                photoAttachmentRatePct = decimal("50.0"),
            ),
        )

        val comparisons = calculator.calculate(WorkType.WATERING, current, previous)

        assertThat(comparisons.map { it.metricKey }).containsExactly(
            "recordCount",
            "workedDayCount",
            "averageIntervalDays",
            "photoAttachmentRatePct",
        )
        assertComparison(comparisons[0], "기록 횟수", "6", "4", "2", "50", "회")
        assertComparison(comparisons[1], "작업한 날짜 수", "5", "4", "1", "25", "일")
        assertComparison(comparisons[2], "평균 작업 간격", "2.5", "4", "-1.5", "-37.5", "일")
        assertComparison(comparisons[3], "사진 첨부 비율", "75", "50", "25", "50", "%")
    }

    @Test
    fun `previous zero omits relative percentage and null metrics are omitted`() {
        val current = CycleReportStatistics(
            watering = WateringStatistics(
                recordCount = 2,
                workedDayCount = 2,
                averageIntervalDays = null,
                photoAttachmentRatePct = decimal("40"),
            ),
        )
        val previous = CycleReportStatistics(
            watering = WateringStatistics(
                recordCount = 0,
                workedDayCount = 0,
                averageIntervalDays = decimal("3"),
                photoAttachmentRatePct = null,
            ),
        )

        val comparisons = calculator.calculate(WorkType.WATERING, current, previous)

        assertThat(comparisons.map { it.metricKey }).containsExactly("recordCount", "workedDayCount")
        assertThat(comparisons).allMatch { it.relativeChangePct == null }
        assertThat(comparisons.map { it.difference }).allSatisfy { assertThat(it).isEqualByComparingTo("2") }
    }

    @Test
    fun `planting compares the same propagation and unit and preserves coverage`() {
        val currentCoverage = Coverage(recordedCount = 2, targetCount = 3)
        val previousCoverage = Coverage(recordedCount = 1, targetCount = 2)
        val current = CycleReportStatistics(
            planting = PlantingStatistics(
                recordCount = 3,
                workedDayCount = 2,
                propagationMethods = listOf(
                    propagation("SEEDLING", "모종", "12", "JU", currentCoverage),
                    propagation("SEED", "씨앗", "300", "G", Coverage(1, 1)),
                ),
            ),
        )
        val previous = CycleReportStatistics(
            planting = PlantingStatistics(
                recordCount = 2,
                workedDayCount = 2,
                propagationMethods = listOf(
                    propagation("SEED", "씨앗", "250", "ML", Coverage(1, 1)),
                    propagation("SEEDLING", "모종", "10", "JU", previousCoverage),
                ),
            ),
        )

        val comparisons = calculator.calculate(WorkType.PLANTING, current, previous)

        assertThat(comparisons.map { it.metricKey }).containsExactly(
            "recordCount",
            "workedDayCount",
            "planting.quantity.SEEDLING.JU",
        )
        val quantity = comparisons.last()
        assertComparison(quantity, "모종 심은 양", "12", "10", "2", "20", "JU")
        assertThat(quantity.currentCoverage).isEqualTo(currentCoverage)
        assertThat(quantity.previousCoverage).isEqualTo(previousCoverage)
    }

    @Test
    fun `fertilizing and harvest compare normalized kilograms only for the selected work`() {
        val current = CycleReportStatistics(
            fertilizing = FertilizingStatistics(
                recordCount = 2,
                workedDayCount = 2,
                totalAmountKg = decimal("1.5000"),
                amountCoverage = Coverage(2, 3),
            ),
            harvest = HarvestStatistics(
                recordCount = 4,
                workedDayCount = 3,
                totalAmountKg = decimal("18.0000"),
                amountCoverage = Coverage(3, 4),
            ),
        )
        val previous = CycleReportStatistics(
            fertilizing = FertilizingStatistics(
                recordCount = 1,
                workedDayCount = 1,
                totalAmountKg = decimal("1.0000"),
                amountCoverage = Coverage(1, 2),
            ),
            harvest = HarvestStatistics(
                recordCount = 3,
                workedDayCount = 3,
                totalAmountKg = decimal("12.0000"),
                amountCoverage = Coverage(3, 3),
            ),
        )

        val fertilizing = calculator.calculate(WorkType.FERTILIZING, current, previous)
        val harvest = calculator.calculate(WorkType.HARVEST, current, previous)

        assertThat(fertilizing.map { it.metricKey }).containsExactly(
            "recordCount",
            "workedDayCount",
            "fertilizing.totalAmountKg",
        )
        assertComparison(fertilizing.last(), "거름 총량", "1.5", "1", "0.5", "50", "KG")
        assertThat(fertilizing.last().currentCoverage).isEqualTo(Coverage(2, 3))
        assertThat(fertilizing).noneMatch { it.metricKey.startsWith("harvest") }

        assertThat(harvest.map { it.metricKey }).containsExactly(
            "recordCount",
            "workedDayCount",
            "harvest.totalAmountKg",
        )
        assertComparison(harvest.last(), "총수확량", "18", "12", "6", "50", "KG")
        assertThat(harvest.last().previousCoverage).isEqualTo(Coverage(3, 3))
        assertThat(harvest).noneMatch { it.metricKey.startsWith("fertilizing") }
    }

    @Test
    fun `pest control matches pesticide units and preserves spray coverage`() {
        val current = CycleReportStatistics(
            pestControl = PestControlStatistics(
                recordCount = 3,
                workedDayCount = 3,
                pesticideAmounts = listOf(
                    AmountByUnit("ML", decimal("120"), Coverage(2, 3)),
                    AmountByUnit("G", decimal("40"), Coverage(1, 3)),
                ),
                totalSprayAmountLiters = decimal("30"),
                sprayAmountCoverage = Coverage(3, 3),
            ),
        )
        val previous = CycleReportStatistics(
            pestControl = PestControlStatistics(
                recordCount = 2,
                workedDayCount = 2,
                pesticideAmounts = listOf(
                    AmountByUnit("G", decimal("35"), Coverage(1, 2)),
                    AmountByUnit("L", decimal("1"), Coverage(1, 2)),
                ),
                totalSprayAmountLiters = decimal("24"),
                sprayAmountCoverage = Coverage(2, 2),
            ),
        )

        val comparisons = calculator.calculate(WorkType.PEST_CONTROL, current, previous)

        assertThat(comparisons.map { it.metricKey }).containsExactly(
            "recordCount",
            "workedDayCount",
            "pestControl.pesticideAmount.G",
            "pestControl.totalSprayAmountLiters",
        )
        val pesticide = comparisons[2]
        assertComparison(pesticide, "약 사용량", "40", "35", "5", "14.29", "G")
        assertThat(pesticide.currentCoverage).isEqualTo(Coverage(1, 3))
        assertThat(pesticide.previousCoverage).isEqualTo(Coverage(1, 2))
        assertThat(comparisons).noneMatch { it.metricKey.endsWith(".ML") || it.metricKey.endsWith(".L") }

        val spray = comparisons.last()
        assertComparison(spray, "약을 섞은 물의 총량", "30", "24", "6", "25", "L")
        assertThat(spray.currentCoverage).isEqualTo(Coverage(3, 3))
        assertThat(spray.previousCoverage).isEqualTo(Coverage(2, 2))
    }

    private fun propagation(
        code: String,
        label: String,
        quantity: String,
        unit: String,
        coverage: Coverage,
    ) = PropagationStatistics(
        code = code,
        label = label,
        recordCount = coverage.targetCount,
        recordRatePct = decimal("100"),
        totalQuantity = decimal(quantity),
        quantityUnit = unit,
        quantityCoverage = coverage,
    )

    private fun assertComparison(
        actual: ReportFeedbackComparison,
        label: String,
        current: String,
        previous: String,
        difference: String,
        relative: String?,
        unit: String,
    ) {
        assertThat(actual.metricLabel).isEqualTo(label)
        assertThat(actual.currentValue).isEqualByComparingTo(current)
        assertThat(actual.previousValue).isEqualByComparingTo(previous)
        assertThat(actual.difference).isEqualByComparingTo(difference)
        if (relative == null) {
            assertThat(actual.relativeChangePct).isNull()
        } else {
            assertThat(actual.relativeChangePct).isEqualByComparingTo(relative)
        }
        assertThat(actual.unit).isEqualTo(unit)
    }

    private fun decimal(value: String) = BigDecimal(value)
}
