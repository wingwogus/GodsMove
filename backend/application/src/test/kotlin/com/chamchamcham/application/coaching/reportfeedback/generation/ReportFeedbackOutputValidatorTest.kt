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
            summary = "이번 물 주기 기록의 흐름을 확인했어요.",
            strengths = emptyList(),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `allows record-backed feedback without a retrieved technical document`() {
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록을 꾸준히 남겼어요.",
            strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.")),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects an unknown evidence reference and a duplicate item without a count cap`() {
        val item = item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.")
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록을 확인했어요.",
            strengths = listOf(item, item),
            improvements = listOf(item("기록 부족", "다음에는 물 준 양을 함께 기록하세요.", "record:${UUID.randomUUID()}")),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .anyMatch { it.startsWith("unknown_evidence:") }
            .contains("duplicate_item")
    }

    @Test
    fun `rejects a summary and item text without friendly honorifics`() {
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록을 꾸준히 남겼습니다.",
            strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인할 수 있습니다.")),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("summary_text_tone", "strength_text_tone")
    }

    @Test
    fun `rejects English while allowing Korean farming terms in public report text`() {
        val content = ReportFeedbackContent(
            summary = "WATERING 흐름을 확인했어요.",
            strengths = listOf(item("DRIP 관수", "DRIP으로 물을 준 점은 좋았어요.")),
            improvements = listOf(item("토양", "토양 수분을 더 확인하세요.")),
            nextActions = listOf(item("방제", "병해충을 방제하세요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("summary_text_english", "strength_text_english")
            .doesNotContain("improvement_text_english", "next_action_text_english")
    }

    @Test
    fun `rejects English in every report item section`() {
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록을 확인했어요.",
            strengths = listOf(item("기록", "DRIP 방식을 꾸준히 사용했어요.")),
            improvements = listOf(item("양", "다음에는 kg 단위 대신 한글로 기록하세요.")),
            nextActions = listOf(item("확인", "pH 대신 흙 상태를 쉬운 말로 적으세요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).contains(
            "strength_text_english",
            "improvement_text_english",
            "next_action_text_english",
        )
    }

    @Test
    fun `allows technical language in internal basis`() {
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록을 확인했어요.",
            strengths = listOf(item("WATERING DRIP 관수", "물 준 방법을 꾸준히 지킨 점은 좋았어요.")),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
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
