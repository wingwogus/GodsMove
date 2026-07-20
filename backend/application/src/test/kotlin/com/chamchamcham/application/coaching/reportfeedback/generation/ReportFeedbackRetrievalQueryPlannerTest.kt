package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackRetrievalQueryPlannerTest {
    @Test
    fun `watering plan uses period and method vocabulary instead of raw counts`() {
        val queries = ReportFeedbackRetrievalQueryPlanner().plan(
            context(
                workType = WorkType.WATERING,
                statistics = mapOf(
                    "recordCount" to 4,
                    "averageIntervalDays" to 3.5,
                    "methodDistribution" to listOf(
                        mapOf("code" to "DRIP", "label" to "점적관수", "count" to 4, "ratePct" to 100),
                    ),
                ),
            ),
        )

        assertThat(queries).allMatch { it.contains("황기") && it.contains("관수") }
        assertThat(queries).contains("황기 관수 재배 관리")
        assertThat(queries).anyMatch { it.contains("3월~7월") && it.contains("시기") }
        assertThat(queries).contains("황기 관수 간격")
        assertThat(queries).contains("황기 점적 관수 방법")
        assertThat(queries.joinToString(" "))
            .doesNotContain("4회")
            .doesNotContain("3.5일")
            .doesNotContain("평균간격")
            .doesNotContain("시비")
            .doesNotContain("방제")
            .doesNotContain("수확량")
    }

    @Test
    fun `pest control plan searches the recorded pest by name`() {
        val queries = ReportFeedbackRetrievalQueryPlanner().plan(
            context(
                workType = WorkType.PEST_CONTROL,
                statistics = mapOf(
                    "recordCount" to 2,
                    "targets" to listOf(
                        mapOf("target" to "진딧물", "count" to 2),
                    ),
                ),
            ),
        )

        assertThat(queries).contains("황기 진딧물 방제")
    }

    @Test
    fun `harvest plan searches the harvested part with document vocabulary`() {
        val queries = ReportFeedbackRetrievalQueryPlanner().plan(
            context(
                workType = WorkType.HARVEST,
                statistics = mapOf(
                    "recordCount" to 1,
                    "medicinalParts" to listOf(
                        mapOf("code" to "ROOT_BARK", "count" to 1),
                    ),
                ),
            ),
        )

        assertThat(queries).contains("황기 뿌리 수확 시기")
        assertThat(queries.joinToString(" ")).doesNotContain("ROOT_BARK")
    }

    @Test
    fun `plan keeps the strongest signals within the query cap`() {
        val queries = ReportFeedbackRetrievalQueryPlanner().plan(
            context(
                workType = WorkType.FERTILIZING,
                statistics = mapOf(
                    "recordCount" to 6,
                    "averageIntervalDays" to 14.0,
                    "materialDistribution" to listOf(
                        mapOf("code" to "액상비료", "label" to "액상비료", "count" to 4),
                        mapOf("code" to "퇴비", "label" to "퇴비", "count" to 1),
                        mapOf("code" to "복합비료", "label" to "복합비료", "count" to 1),
                    ),
                    "methodDistribution" to listOf(
                        mapOf("code" to "SOIL", "count" to 5),
                        mapOf("code" to "FOLIAR", "count" to 1),
                    ),
                ),
            ),
        )

        assertThat(queries).hasSizeLessThanOrEqualTo(5)
        assertThat(queries).contains("황기 액상비료 시비")
        assertThat(queries.joinToString(" ")).doesNotContain("복합비료")
    }

    @Test
    fun `unknown detail codes are omitted instead of leaking raw codes`() {
        val queries = ReportFeedbackRetrievalQueryPlanner().plan(
            context(
                workType = WorkType.WATERING,
                statistics = mapOf(
                    "recordCount" to 1,
                    "methodDistribution" to listOf(
                        mapOf("code" to "NEW_METHOD", "count" to 1),
                    ),
                ),
            ),
        )

        assertThat(queries.joinToString(" ")).doesNotContain("NEW_METHOD")
    }

    private fun context(
        workType: WorkType,
        statistics: Map<String, Any?>,
    ) = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = workType,
        report = ReportFeedbackReport(
            id = UUID.randomUUID(),
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            statistics = statistics,
        ),
        records = listOf(
            ReportFeedbackRecord(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                workType,
                "메모",
                emptyMap(),
            ),
        ),
        previousReport = null,
        warnings = emptyList(),
    )
}
