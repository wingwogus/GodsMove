package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.report.CycleReportStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackPromptBuilderTest {
    private val recordId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()

    @Test
    fun `prompt lists the exact evidence references accepted by validation`() {
        val prompt = ReportFeedbackPromptBuilder().build(
            context = context(),
            evidence = listOf(
                ReportFeedbackEvidence(
                    id = "document-1",
                    title = "황기 재배 기술",
                    content = "배수가 잘되는 토양을 선택한다.",
                ),
            ),
        )

        assertThat(prompt.system)
            .contains("evidenceRefs에는 허용 evidenceRefs에 나열된 값을 정확히 그대로 사용한다.")
            .contains("통계 필드명이나 통계값은 evidenceRefs로 사용하지 않는다.")
        assertThat(prompt.user)
            .contains(
                """
                    허용 evidenceRefs:
                    - record:$recordId : 대상 영농기록
                    - report:$previousReportId : 직전 완료 리포트
                    - document-1 : 황기 재배 기술
                """.trimIndent(),
            )
    }

    private fun context() = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        report = ReportFeedbackReport(
            id = UUID.randomUUID(),
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            statistics = CycleReportStatistics.empty(),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = recordId,
                workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                workType = "WATERING",
                memo = "관수",
                details = emptyMap(),
            ),
        ),
        previousReport = ReportFeedbackPreviousReport(
            id = previousReportId,
            startsAt = LocalDateTime.of(2025, 11, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 2, 1, 9, 0),
            statistics = CycleReportStatistics.empty(),
        ),
        warnings = emptyList(),
    )
}
