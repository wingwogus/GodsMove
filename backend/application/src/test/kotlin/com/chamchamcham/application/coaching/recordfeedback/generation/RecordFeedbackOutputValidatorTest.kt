package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackOutputValidatorTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val context = readFixture("today-record-feedback-watering.json")
    private val documents = listOf(
        RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다.")
    )

    @Test
    fun `validates exactly one good point and two to three cited actions`() {
        val warnings = RecordFeedbackOutputValidator.validate(validResult(), context, documents)

        assertThat(warnings).isEmpty()
    }

    @Test
    fun `rejects text below 15 characters and unknown evidence`() {
        val invalid = validResult().copy(
            goodPoint = validItem(text = "짧음", refs = listOf("unknown")),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
            .contains("good_point_text_length", "unknown_evidence")
    }

    @Test
    fun `rejects text that does not end in a friendly honorific`() {
        val invalid = validResult().copy(
            goodPoint = validItem(text = "흙 상태를 꼼꼼하게 확인했다."),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
            .contains("good_point_text_tone")
    }

    @Test
    fun `rejects weather action without weather evidence`() {
        val invalid = validResult(
            category = RecordFeedbackActionCategory.WEATHER,
            refs = listOf(context.recordCitationId()),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
            .contains("weather_action_without_weather_evidence")
    }

    @Test
    fun `allows a hidden basis while rejecting blank fields and wrong action count`() {
        val invalid = RecordFeedbackContent(
            goodPoint = validItem(basis = " ", text = "흙 상태를 살핀 점은 잘했어요."),
            nextActions = listOf(
                validAction(
                    basis = "비 예보",
                    text = "배수로 막힌 흙을 치우세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
            ),
        )

        val warnings = RecordFeedbackOutputValidator.validate(invalid, context, documents)

        assertThat(warnings)
            .contains(
                "good_point_basis_blank",
                "action_count",
            )
        assertThat(warnings).doesNotContain("next_action_0_basis_token_missing")
    }

    @Test
    fun `accepts text at exact 15 and 60 character boundaries`() {
        val valid = validResult().copy(
            goodPoint = validItem(basis = "토양", text = textOfLengthWithBasis("토양", 15)),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비예보",
                    text = textOfLengthWithBasis("비예보", 60),
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(basis = "토양", text = textOfLengthWithBasis("토양", 15)),
            ),
        )

        val warnings = RecordFeedbackOutputValidator.validate(valid, context, documents)

        assertThat(warnings).doesNotContain(
            "good_point_text_length",
            "next_action_0_text_length",
            "next_action_1_text_length",
        )
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `rejects text at 14 and 61 character boundaries`() {
        val invalid = validResult().copy(
            goodPoint = validItem(basis = "토양", text = textOfLengthWithBasis("토양", 14)),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비예보",
                    text = textOfLengthWithBasis("비예보", 61),
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(),
            ),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
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
                    text = "비 오기 전 물길을 정리하세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(),
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.PEST_DISEASE,
                    basis = "토양 상태",
                    text = "잎 앞뒤를 자세히 살펴보세요.",
                    refs = listOf("doc-1"),
                ),
            ),
        )
        val fourActions = threeActions.copy(nextActions = threeActions.nextActions + validAction())

        assertThat(RecordFeedbackOutputValidator.validate(threeActions, context, documents)).isEmpty()
        assertThat(RecordFeedbackOutputValidator.validate(fourActions, context, documents))
            .contains("action_count")
    }

    @Test
    fun `rejects blank text and blank evidence ref`() {
        val invalid = validResult().copy(
            goodPoint = validItem(text = " ", refs = listOf(" ")),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
            .contains("good_point_text_blank", "good_point_evidence_ref_blank")
    }

    @Test
    fun `does not allow blank document ids as evidence refs`() {
        val blankDocument = RecordFeedbackEvidence(" ", "빈 문서", null, "빈 문서 ID는 근거가 될 수 없다.")
        val invalid = validResult().copy(
            goodPoint = validItem(refs = listOf(" ")),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents + blankDocument))
            .contains("good_point_evidence_ref_blank")
    }

    @Test
    fun `rejects pest disease action without document evidence`() {
        val invalid = validResult(
            category = RecordFeedbackActionCategory.PEST_DISEASE,
            refs = listOf(context.recordCitationId()),
        )

        assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
            .contains("pest_disease_action_without_document_evidence")
    }

    private fun validResult(
        category: RecordFeedbackActionCategory = RecordFeedbackActionCategory.IRRIGATION,
        refs: List<String> = listOf(context.recordCitationId(), "doc-1"),
    ): RecordFeedbackContent {
        return RecordFeedbackContent(
            goodPoint = validItem(),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비 예보",
                    text = "비 오기 전 물길을 정리하세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = category,
                    basis = "토양 상태",
                    text = "다음에 흙 상태를 살펴보세요.",
                    refs = refs,
                ),
            ),
        )
    }

    private fun validItem(
        basis: String = "점적관수",
        text: String = "흙 상태를 살핀 점은 잘했어요.",
        refs: List<String> = listOf(context.recordCitationId()),
    ): RecordFeedbackGoodPoint {
        return RecordFeedbackGoodPoint(
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private fun validAction(
        due: RecordFeedbackActionDue = RecordFeedbackActionDue.NEXT_CHECK,
        category: RecordFeedbackActionCategory = RecordFeedbackActionCategory.IRRIGATION,
        basis: String = "토양 상태",
        text: String = "다음에 흙 상태를 살펴보세요.",
        refs: List<String> = listOf(context.recordCitationId(), "doc-1"),
    ): RecordFeedbackAction {
        return RecordFeedbackAction(
            due = due,
            category = category,
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private fun textOfLengthWithBasis(basis: String, length: Int): String {
        require(basis.length < length)
        return basis + "가".repeat(length - basis.length - 1) + "요"
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
