package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.report.CycleReportStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackOutputValidatorTest {
    private val reportId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val context = ReportFeedbackContext(
        schemaVersion = 1,
        report = ReportFeedbackReport(
            id = reportId,
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
        previousReport = null,
        warnings = emptyList(),
    )

    @Test
    fun `allows record-backed feedback without a retrieved technical document`() {
        val content = ReportFeedbackContent(
            summary = "이번 사이클에는 관수 기록을 꾸준히 남겼어요.",
            strengths = listOf(item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인할 수 있어요.")),
            improvements = emptyList(),
            nextCycleActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects an unknown evidence reference and a duplicate item without a count cap`() {
        val item = item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인할 수 있어요.")
        val content = ReportFeedbackContent(
            summary = "이번 사이클 요약이에요.",
            strengths = listOf(item, item),
            improvements = listOf(item("기록 부족", "다음 사이클에는 추가 작업을 기록하세요.", "unknown")),
            nextCycleActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("duplicate_item", "unknown_evidence:unknown")
    }

    @Test
    fun `rejects a summary and item text without friendly honorifics`() {
        val content = ReportFeedbackContent(
            summary = "이번 사이클은 관수 기록을 꾸준히 남겼습니다.",
            strengths = listOf(item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인할 수 있습니다.")),
            improvements = emptyList(),
            nextCycleActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("summary_text_tone", "strength_text_tone")
    }

    private fun item(
        basis: String,
        text: String,
        evidenceRef: String = "record:$recordId",
    ) = ReportFeedbackContentItem(
        basis = basis,
        text = text,
        evidenceRefs = listOf(evidenceRef),
    )
}
