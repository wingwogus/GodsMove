package com.chamchamcham.application.coaching.rag.record

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackPromptBuilderTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val builder = RecordFeedbackPromptBuilder()

    @Test
    fun `prompt includes safety rules for medicinal crop record feedback`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.system).contains("약용작물 영농기록 피드백")
        assertThat(prompt.system).contains("사진은 분석하지 않는다")
        assertThat(prompt.system).contains("의학적 효능")
        assertThat(prompt.system).contains("정확한 비료량이나 농약량을 invent하지 않는다")
    }

    @Test
    fun `prompt includes target record weather stats recent records and evidence`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.user).contains("작물: 참당귀")
        assertThat(prompt.user).contains("작업유형: 물주기")
        assertThat(prompt.user).contains("오전 흙 표면이 말라 보여 점적 관수함.")
        assertThat(prompt.user).contains("최근 7일 강수량: 4.5mm")
        assertThat(prompt.user).contains("WATERING=8")
        assertThat(prompt.user).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }
}
