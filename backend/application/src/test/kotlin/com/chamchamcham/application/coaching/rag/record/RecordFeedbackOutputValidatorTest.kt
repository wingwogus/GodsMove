package com.chamchamcham.application.coaching.rag.record

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackOutputValidatorTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val validator = RecordFeedbackOutputValidator()
    private val context = readFixture("today-record-feedback-watering.json")
    private val documents = listOf(
        RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다.")
    )

    @Test
    fun `validates exactly one good point and two to three cited actions`() {
        val validation = validator.validate(validResult(), validator.allowedEvidenceRefs(context, documents))

        assertThat(validation.isValid).isTrue()
        assertThat(validation.warnings).isEmpty()
    }

    @Test
    fun `rejects text outside 15 to 45 characters and unknown evidence`() {
        val invalid = validResult().copy(
            goodPoint = validItem(text = "짧음", refs = listOf("unknown")),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("good_point_text_length", "unknown_evidence:unknown")
    }

    @Test
    fun `rejects weather action without weather evidence`() {
        val invalid = validResult(
            category = RecordFeedbackActionCategory.WEATHER,
            refs = listOf(context.recordCitationId()),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("weather_action_without_weather_evidence")
    }

    @Test
    fun `rejects blank fields wrong action count and missing basis token`() {
        val invalid = RecordFeedbackCoachingResult(
            goodPoint = validItem(basis = " ", text = "점적관수로 토양 상태를 확인한 점이 좋았어요."),
            nextActions = listOf(
                validAction(
                    basis = "비 예보",
                    text = "배수로 주변을 먼저 확인해 주세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
            ),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains(
                "good_point_basis_blank",
                "action_count",
                "next_action_0_basis_token_missing",
            )
    }

    private fun validResult(
        category: RecordFeedbackActionCategory = RecordFeedbackActionCategory.IRRIGATION,
        refs: List<String> = listOf(context.recordCitationId(), "doc-1"),
    ): RecordFeedbackCoachingResult {
        return RecordFeedbackCoachingResult(
            goodPoint = validItem(),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비 예보",
                    text = "비 예보 전 배수로 막힘을 먼저 확인하세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = category,
                    basis = "토양 상태",
                    text = "다음 점검 때 토양 상태를 다시 살펴보세요.",
                    refs = refs,
                ),
            ),
        )
    }

    private fun validItem(
        basis: String = "점적관수",
        text: String = "점적관수로 토양 상태를 확인한 점이 좋았어요.",
        refs: List<String> = listOf(context.recordCitationId()),
    ): RecordFeedbackItem {
        return RecordFeedbackItem(
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private fun validAction(
        due: RecordFeedbackActionDue = RecordFeedbackActionDue.NEXT_CHECK,
        category: RecordFeedbackActionCategory = RecordFeedbackActionCategory.IRRIGATION,
        basis: String = "토양 상태",
        text: String = "다음 점검 때 토양 상태를 다시 살펴보세요.",
        refs: List<String> = listOf(context.recordCitationId(), "doc-1"),
    ): RecordFeedbackNextAction {
        return RecordFeedbackNextAction(
            due = due,
            category = category,
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
