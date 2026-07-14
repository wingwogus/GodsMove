package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackOutputValidatorTest {
    private val reportId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()
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
        previousReport = ReportFeedbackPreviousReport(
            id = previousReportId,
            startsAt = LocalDateTime.of(2025, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2025, 7, 1, 9, 0),
            statistics = mapOf("recordCount" to 1),
        ),
        comparisons = listOf(serverComparison()),
        warnings = emptyList(),
    )

    @Test
    fun `rejects generated comparison when the server has no comparable difference`() {
        val unavailableContext = context.copy(previousReport = null, comparisons = emptyList())
        val content = contentWithComparison(
            evidenceRefs = listOf("report:$reportId"),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, unavailableContext, emptyList()))
            .containsExactly("comparison_not_available")
    }

    @Test
    fun `requires the current report reference on every comparison`() {
        val content = contentWithComparison(
            evidenceRefs = listOf("report:$previousReportId"),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("comparison_current_report_ref_required")
    }

    @Test
    fun `requires the previous report reference on every comparison`() {
        val content = contentWithComparison(
            evidenceRefs = listOf("report:$reportId"),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("comparison_previous_report_ref_required")
    }

    @Test
    fun `record evidence alone cannot ground a comparison`() {
        val content = contentWithComparison(
            evidenceRefs = listOf("record:$recordId"),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).containsExactly(
            "comparison_current_report_ref_required",
            "comparison_previous_report_ref_required",
        )
    }

    @Test
    fun `allows polite Korean comparison grounded in current and previous reports`() {
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록의 흐름을 확인했어요.",
            comparisons = listOf(
                ReportFeedbackContentItem(
                    basis = "직전보다 기록 1회 증가",
                    text = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
                    evidenceRefs = listOf("report:$reportId", "report:$previousReportId"),
                ),
            ),
            strengths = emptyList(),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects informal English and unknown evidence in comparison`() {
        val unknown = "report:${UUID.randomUUID()}"
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록의 흐름을 확인했어요.",
            comparisons = listOf(
                ReportFeedbackContentItem(
                    basis = "comparison",
                    text = "WATERING 기록이 늘었다.",
                    evidenceRefs = listOf(unknown),
                ),
            ),
            strengths = emptyList(),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("comparison_text_tone", "comparison_text_english", "unknown_evidence:$unknown")
    }

    @Test
    fun `rejects the same fact repeated across comparison and another section`() {
        val comparison = ReportFeedbackContentItem(
            basis = "직전보다 기록 1회 증가",
            text = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
            evidenceRefs = listOf("report:$reportId", "report:$previousReportId"),
        )
        val repeatedWithDifferentBasis = comparison.copy(basis = "이번 기록과 직전 기록의 차이")
        val content = ReportFeedbackContent(
            summary = "이번 물 주기 기록의 흐름을 확인했어요.",
            comparisons = listOf(comparison),
            strengths = listOf(repeatedWithDifferentBasis),
            improvements = emptyList(),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("duplicate_item")
    }

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

    private fun contentWithComparison(evidenceRefs: List<String>) = ReportFeedbackContent(
        summary = "이번 물 주기 기록의 흐름을 확인했어요.",
        comparisons = listOf(
            ReportFeedbackContentItem(
                basis = "직전보다 기록 1회 증가",
                text = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
                evidenceRefs = evidenceRefs,
            ),
        ),
        strengths = emptyList(),
        improvements = emptyList(),
        nextActions = emptyList(),
    )

    private fun serverComparison() = ReportFeedbackComparison(
        metricKey = "recordCount",
        metricLabel = "기록 횟수",
        currentValue = BigDecimal("2"),
        previousValue = BigDecimal("1"),
        difference = BigDecimal("1"),
        relativeChangePct = BigDecimal("100"),
        unit = "회",
    )
}
