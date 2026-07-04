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
        assertThat(prompt.system).contains("citationIds는 허용 citationIds에 명시된 값만 사용한다")
        assertThat(prompt.system).contains("귀농 청년도 바로 이해할 수 있는 쉬운 말")
        assertThat(prompt.system).contains("불확실한 판단은 단정하지 않는다")
        assertThat(prompt.system).contains("수확 후 가공, 건조, 저장 조언은 보조 점검 수준")
        assertThat(prompt.system).contains("예보는 확정된 날씨처럼 단정하지 않는다")
        assertThat(prompt.system).contains("forecast7Days에 강우, 고온, 고습, 건조, 강풍 신호가 있으면 nextActions에 예보 기반 점검 행동을 포함한다")
        assertThat(prompt.system).contains("summary, diagnosis, observations, recommendations, nextActions에는 chunkId나 UUID를 직접 쓰지 않는다")
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
        assertThat(prompt.user).contains("예보: 2026-07-04 강수 22.0mm")
        assertThat(prompt.user).contains("riskFlags=HEAVY_RAIN,HIGH_HUMIDITY")
        assertThat(prompt.user).contains("WATERING=8")
        assertThat(prompt.user).contains("허용 citationIds:")
        assertThat(prompt.user).contains("record:feedback-20260703-watering : 당일 영농기록 context")
        assertThat(prompt.user).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
        assertThat(prompt.user).contains("영농 경력: 1")
        assertThat(prompt.user).contains("경영 형태: NON_REGISTERED_FARMER")
        assertThat(prompt.user).contains("최저 19.9C")
        assertThat(prompt.user).contains("최저 21.4C")
        assertThat(prompt.user).contains("유형별 마지막 작업일:")
        assertThat(prompt.user).contains("WATERING=2026-06-30")
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }
}
