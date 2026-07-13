package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackPromptBuilderTest {
    private val recordId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()

    @Test
    fun `prompt scopes instructions statistics and allowed evidence to one work type`() {
        val prompt = ReportFeedbackPromptBuilder().build(
            context = context(),
            evidence = listOf(
                ReportFeedbackEvidence(
                    id = "document-1",
                    title = "황기 관수 기술",
                    content = "관수 후 토양 수분을 확인한다.",
                ),
            ),
        )

        assertThat(prompt.system)
            .contains("대상 작업 타입 하나만")
            .contains("nextActions")
            .contains("빈 배열")
            .contains("summary와 모든 text는 친근한 존댓말로 끝낸다.")
        assertThat(prompt.user)
            .contains("작업 타입: WATERING")
            .contains("recordCount=4")
            .contains("record:$recordId")
            .contains("report:$previousReportId")
            .contains("document-1")
            .doesNotContain("FERTILIZING")
            .doesNotContain("수확량")
    }

    private fun context() = ReportFeedbackContext(
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
                id = recordId,
                workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                workType = WorkType.WATERING,
                memo = "점적관수를 했어요.",
                details = emptyMap(),
            ),
        ),
        previousReport = ReportFeedbackPreviousReport(
            id = previousReportId,
            startsAt = LocalDateTime.of(2025, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2025, 7, 1, 9, 0),
            statistics = mapOf("recordCount" to 3),
        ),
        warnings = emptyList(),
    )
}
