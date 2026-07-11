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

    @Test
    fun `accepts text at exact 15 and 45 character boundaries`() {
        val valid = validResult().copy(
            goodPoint = validItem(basis = "토양", text = textOfLengthWithBasis("토양", 15)),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비예보",
                    text = textOfLengthWithBasis("비예보", 45),
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(basis = "토양", text = textOfLengthWithBasis("토양", 15)),
            ),
        )

        val validation = validator.validate(valid, validator.allowedEvidenceRefs(context, documents))

        assertThat(validation.warnings).doesNotContain(
            "good_point_text_length",
            "next_action_0_text_length",
            "next_action_1_text_length",
        )
        assertThat(validation.isValid).isTrue()
    }

    @Test
    fun `rejects text at 14 and 46 character boundaries`() {
        val invalid = validResult().copy(
            goodPoint = validItem(basis = "토양", text = textOfLengthWithBasis("토양", 14)),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비예보",
                    text = textOfLengthWithBasis("비예보", 46),
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(),
            ),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("good_point_text_length", "next_action_0_text_length")
    }

    @Test
    fun `accepts three actions and rejects four actions`() {
        val threeActions = validResult().copy(
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비 예보",
                    text = "비 예보 전 배수로 막힘을 먼저 확인하세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(),
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.PEST_DISEASE,
                    basis = "토양 상태",
                    text = "토양 상태 변화와 병해 흔적을 함께 보세요.",
                    refs = listOf("doc-1"),
                ),
            ),
        )
        val fourActions = threeActions.copy(nextActions = threeActions.nextActions + validAction())

        assertThat(validator.validate(threeActions, validator.allowedEvidenceRefs(context, documents)).isValid).isTrue()
        assertThat(validator.validate(fourActions, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("action_count")
    }

    @Test
    fun `rejects blank text and blank evidence ref`() {
        val invalid = validResult().copy(
            goodPoint = validItem(text = " ", refs = listOf(" ")),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("good_point_text_blank", "good_point_evidence_ref_blank")
    }

    @Test
    fun `does not allow blank document ids as evidence refs`() {
        val blankDocument = RecordFeedbackEvidence(" ", "빈 문서", null, "빈 문서 ID는 근거가 될 수 없다.")
        val allowedEvidenceRefs = validator.allowedEvidenceRefs(context, documents + blankDocument)
        val invalid = validResult().copy(
            goodPoint = validItem(refs = listOf(" ")),
        )

        assertThat(allowedEvidenceRefs.ids).doesNotContain(" ")
        assertThat(allowedEvidenceRefs.documentIds).doesNotContain(" ")
        assertThat(validator.validate(invalid, allowedEvidenceRefs).warnings)
            .contains("good_point_evidence_ref_blank")
    }

    @Test
    fun `rejects pest disease action without document evidence`() {
        val invalid = validResult(
            category = RecordFeedbackActionCategory.PEST_DISEASE,
            refs = listOf(context.recordCitationId()),
        )

        assertThat(validator.validate(invalid, validator.allowedEvidenceRefs(context, documents)).warnings)
            .contains("pest_disease_action_without_document_evidence")
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

    private fun textOfLengthWithBasis(basis: String, length: Int): String {
        require(basis.length <= length)
        return basis + "가".repeat(length - basis.length)
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
