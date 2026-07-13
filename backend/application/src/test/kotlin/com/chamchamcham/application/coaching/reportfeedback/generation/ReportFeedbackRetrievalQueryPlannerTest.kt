package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.TargetCount
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackRetrievalQueryPlannerTest {
    @Test
    fun `plan includes report signals as well as crop and work types`() {
        val context = ReportFeedbackContext(
            schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            report = ReportFeedbackReport(
                id = UUID.randomUUID(), farmName = "약초농장", cropName = "황기",
                startsAt = LocalDateTime.of(2026, 3, 1, 9, 0), endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
                statistics = CycleReportStatistics(
                    watering = WateringStatistics(recordCount = 4, averageIntervalDays = BigDecimal("3.5")),
                    pestControl = PestControlStatistics(targets = listOf(TargetCount("진딧물", 2))),
                    harvest = HarvestStatistics(totalAmountKg = BigDecimal("30")),
                ),
            ),
            records = listOf(ReportFeedbackRecord(UUID.randomUUID(), LocalDateTime.of(2026, 4, 1, 9, 0), "WATERING", "관수", emptyMap())),
            previousReport = null,
            warnings = emptyList(),
        )

        assertThat(ReportFeedbackRetrievalQueryPlanner().plan(context))
            .anyMatch { it.contains("관수 4회") }
            .anyMatch { it.contains("진딧물") }
            .anyMatch { it.contains("수확량 30kg") }
    }
}
