package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.RecordFeedbackActionDue

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackContentTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `coaching result keeps one good point and typed next actions in JSON`() {
        val json = objectMapper.writeValueAsString(validResult())
        val root = objectMapper.readTree(json)
        val goodPoint = root["goodPoint"]
        val nextAction = root["nextActions"][0]

        assertThat(root.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "goodPoint",
            "nextActions",
        )
        assertThat(goodPoint.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "basis",
            "text",
            "evidenceRefs",
        )
        assertThat(nextAction.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "due",
            "category",
            "basis",
            "text",
            "evidenceRefs",
        )
        assertThat(nextAction["due"].asText()).isEqualTo("NEXT_WEEK")
        assertThat(nextAction["category"].asText()).isEqualTo("WEATHER")
        assertThat(RecordFeedbackActionDue.entries.map { it.name }).containsExactly(
            "TODAY",
            "THIS_WEEK",
            "NEXT_WEEK",
            "NEXT_CHECK",
        )
        assertThat(RecordFeedbackActionCategory.entries.map { it.name }).containsExactly(
            "WEATHER",
            "PEST_DISEASE",
            "IRRIGATION",
            "FERTILIZING",
            "PEST_CONTROL",
            "HARVEST",
            "CULTIVATION",
            "GENERAL",
        )
    }

    private fun validResult(): RecordFeedbackContent {
        return RecordFeedbackContent(
            goodPoint = RecordFeedbackGoodPoint(
                basis = "점적관수 방식과 토양 상태 기록",
                text = "점적관수로 토양 상태를 확인한 점이 좋았어요.",
                evidenceRefs = listOf("record:record-1"),
            ),
            nextActions = listOf(
                RecordFeedbackAction(
                    due = RecordFeedbackActionDue.NEXT_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "7월 15일 비 예보",
                    text = "7월 15일 비 예보가 있어 배수로를 확인하세요.",
                    evidenceRefs = listOf("weather:2026-07-15"),
                ),
                RecordFeedbackAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = RecordFeedbackActionCategory.IRRIGATION,
                    basis = "토양 상태 확인 기록",
                    text = "다음 확인 때 토양 수분을 다시 살펴보세요.",
                    evidenceRefs = listOf("record:record-1"),
                ),
            ),
        )
    }
}
