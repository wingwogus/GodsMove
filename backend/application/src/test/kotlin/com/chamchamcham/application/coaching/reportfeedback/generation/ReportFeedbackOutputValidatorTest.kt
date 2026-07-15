package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
        val content = validContent()

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `allows stylistic deviations but still rejects unknown evidence in comparison`() {
        val unknown = "report:${UUID.randomUUID()}"
        val content = validContent().copy(
            comparisons = listOf(
                comparisonItem(
                    text = "WATERING 기록이 늘었다.",
                    evidenceRefs = listOf("report:$reportId", "report:$previousReportId", unknown),
                ),
            ),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("unknown_evidence:$unknown")
    }

    @Test
    fun `allows the same fact repeated across comparison and another section`() {
        val comparison = ReportFeedbackContentItem(
            basis = "직전보다 기록 1회 증가",
            text = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
            evidenceRefs = listOf("report:$reportId", "report:$previousReportId"),
        )
        val repeatedWithDifferentBasis = comparison.copy(basis = "이번 기록과 직전 기록의 차이")
        val content = validContent().copy(
            comparisons = listOf(comparison),
            strengths = listOf(repeatedWithDifferentBasis),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .isEmpty()
    }

    @Test
    fun `requires exactly one item for each mandatory paragraph section`() {
        val content = validContent().copy(
            strengths = emptyList(),
            improvements = listOf(
                item(),
                item(text = "흙 속 수분도 함께 확인할 필요가 있어요."),
            ),
            nextActions = emptyList(),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("strength_count", "improvement_count", "next_action_count")
    }

    @Test
    fun `requires one comparison paragraph when server comparison exists`() {
        val content = validContent(comparisons = emptyList())

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("comparison_count")
    }

    @Test
    fun `allows no comparison paragraph when server comparison is unavailable`() {
        val unavailableContext = context.copy(previousReport = null, comparisons = emptyList())
        val content = validContent(comparisons = emptyList())

        assertThat(ReportFeedbackOutputValidator.validate(content, unavailableContext, emptyList()))
            .isEmpty()
    }

    @Test
    fun `rejects line breaks inside a section paragraph`() {
        val content = validContent().copy(
            nextActions = listOf(
                item(text = "다음에는 흙 속을 확인하세요.\n젖은 깊이도 함께 살펴보세요."),
            ),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly("next_action_text_paragraph")
    }

    @Test
    fun `allows record-backed feedback without a retrieved technical document`() {
        val content = validContent().copy(
            summary = "이번 물 주기 기록을 꾸준히 남겼어요.",
            strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects multiple items and an unknown evidence reference`() {
        val duplicate = item()
        val unknown = "record:${UUID.randomUUID()}"
        val content = validContent().copy(
            strengths = listOf(duplicate, duplicate),
            improvements = listOf(item(evidenceRef = unknown)),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .contains("strength_count", "unknown_evidence:$unknown")
    }

    @Test
    fun `allows a summary and item text with formal honorifics`() {
        val content = validContent().copy(
            summary = "이번 물 주기 기록을 꾸준히 남겼습니다.",
            strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인할 수 있습니다.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .isEmpty()
    }

    @Test
    fun `allows English and Korean farming terms in public report text`() {
        val content = validContent().copy(
            summary = "WATERING 흐름을 확인했어요.",
            strengths = listOf(item("DRIP 관수", "DRIP으로 물을 준 점은 좋았어요.")),
            improvements = listOf(item("토양", "토양 수분을 더 확인하세요.")),
            nextActions = listOf(item("방제", "병해충을 방제하세요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .isEmpty()
    }

    @Test
    fun `allows English in every report item section`() {
        val content = validContent().copy(
            summary = "이번 물 주기 기록을 확인했어요.",
            strengths = listOf(item("기록", "DRIP 방식을 꾸준히 사용했어요.")),
            improvements = listOf(item("양", "다음에는 kg 단위 대신 한글로 기록하세요.")),
            nextActions = listOf(item("확인", "pH 대신 흙 상태를 쉬운 말로 적으세요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `allows summary and every section text at exactly fifty characters`() {
        val text = "가".repeat(50)
        val content = validContent().copy(
            summary = text,
            comparisons = listOf(comparisonItem(text = text)),
            strengths = listOf(item(text = text)),
            improvements = listOf(item(text = text)),
            nextActions = listOf(item(text = text)),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    @Test
    fun `rejects summary and every section text over fifty characters`() {
        val text = "가".repeat(51)
        val content = validContent().copy(
            summary = text,
            comparisons = listOf(comparisonItem(text = text)),
            strengths = listOf(item(text = text)),
            improvements = listOf(item(text = text)),
            nextActions = listOf(item(text = text)),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
            .containsExactly(
                "summary_text_length",
                "comparison_text_length",
                "strength_text_length",
                "improvement_text_length",
                "next_action_text_length",
            )
    }

    @Test
    fun `deserializes omitted item sections as empty lists`() {
        val content = jacksonObjectMapper().readValue<ReportFeedbackContent>(
            """{"summary":"이번 물 주기 기록을 확인했어요."}""",
        )

        assertThat(content.comparisons).isEmpty()
        assertThat(content.strengths).isEmpty()
        assertThat(content.improvements).isEmpty()
        assertThat(content.nextActions).isEmpty()
    }

    @Test
    fun `allows technical language in internal basis`() {
        val content = validContent().copy(
            summary = "이번 물 주기 기록을 확인했어요.",
            strengths = listOf(item("WATERING DRIP 관수", "물 준 방법을 꾸준히 지킨 점은 좋았어요.")),
        )

        assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
    }

    private fun item(
        basis: String = "관수 1회",
        text: String = "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.",
        evidenceRef: String = "record:$recordId",
    ) = ReportFeedbackContentItem(
        basis = basis,
        text = text,
        evidenceRefs = listOf(evidenceRef),
    )

    private fun comparisonItem(
        evidenceRefs: List<String> = listOf("report:$reportId", "report:$previousReportId"),
        text: String = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
    ) = ReportFeedbackContentItem(
        basis = "직전보다 기록 1회 증가",
        text = text,
        evidenceRefs = evidenceRefs,
    )

    private fun validContent(
        comparisons: List<ReportFeedbackContentItem> = listOf(comparisonItem()),
    ) = ReportFeedbackContent(
        summary = "이번 물 주기 기록의 흐름을 확인했어요.",
        comparisons = comparisons,
        strengths = listOf(item()),
        improvements = listOf(
            item("물의 양", "물의 양이 알맞았는지 더 살펴볼 필요가 있어요."),
        ),
        nextActions = listOf(
            item("흙 속 수분", "다음에는 물을 준 뒤 흙 속까지 젖었는지 확인하세요."),
        ),
    )

    private fun contentWithComparison(evidenceRefs: List<String>) = validContent(
        comparisons = listOf(comparisonItem(evidenceRefs = evidenceRefs)),
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
