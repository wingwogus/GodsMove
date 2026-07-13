package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackRetrievalQueryPlannerTest {
    @Test
    fun `watering plan contains only watering signals`() {
        val context = ReportFeedbackContext(
            schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            workType = WorkType.WATERING,
            report = ReportFeedbackReport(
                id = UUID.randomUUID(),
                farmName = "약초농장",
                cropName = "황기",
                startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
                endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
                statistics = mapOf("recordCount" to 4, "averageIntervalDays" to 3.5),
            ),
            records = listOf(
                ReportFeedbackRecord(
                    UUID.randomUUID(),
                    LocalDateTime.of(2026, 4, 1, 9, 0),
                    WorkType.WATERING,
                    "관수",
                    emptyMap(),
                ),
            ),
            previousReport = null,
            warnings = emptyList(),
        )

        val queries = ReportFeedbackRetrievalQueryPlanner().plan(context)

        assertThat(queries).allMatch { it.contains("황기") && it.contains("관수") }
        assertThat(queries).anyMatch { it.contains("4회") }
        assertThat(queries.joinToString(" "))
            .doesNotContain("시비")
            .doesNotContain("방제")
            .doesNotContain("수확량")
    }
}
