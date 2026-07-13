package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackOutputValidatorTest {
    private val reportId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val context = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = WorkType.WATERING,
        report = ReportFeedbackReport(
            id = reportId,
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            statistics = mapOf("recordCount" to 1),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = recordId,
                workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                workType = WorkType.WATERING,
                memo = "관수",
                details = emptyMap(),
            ),
        ),
        previousReport = null,
        warnings = emptyList(),
    )

    @Test
    fun `allows a grounded summary when every item list is empty`() {
        val content = ReportFeedbackContent(
            summary = "이번 관수 기록의 흐름을 확인했어요.",
            strengths = emptyList(),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `allows record-backed feedback without a retrieved technical document`() {
        val content = ReportFeedbackContent(
            summary = "이번 관수 기록을 꾸준히 남겼어요.",
            strengths = listOf(item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인하기 좋았어요.")),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects an unknown evidence reference and a duplicate item without a count cap`() {
        val item = item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인하기 좋았어요.")
        val content = ReportFeedbackContent(
            summary = "이번 관수 기록을 확인했어요.",
            strengths = listOf(item, item),
            improvements = listOf(item("기록 부족", "다음에는 추가 관수 정보를 기록하세요.", "record:${UUID.randomUUID()}")),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .anyMatch { it.startsWith("unknown_evidence:") }
            .contains("duplicate_item")
    }

    @Test
    fun `rejects a summary and item text without friendly honorifics`() {
        val content = ReportFeedbackContent(
            summary = "이번 관수 기록을 꾸준히 남겼습니다.",
            strengths = listOf(item("관수 1회", "관수 기록을 남겨 작업 흐름을 확인할 수 있습니다.")),
            improvements = emptyList(),
            nextActions = emptyList(),
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
