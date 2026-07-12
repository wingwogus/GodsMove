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
        assertThat(prompt.system).contains("잘한 점은 정확히 1개만 작성한다")
        assertThat(prompt.system).contains("다음 행동은 2~3개만 작성한다")
        assertThat(prompt.system).contains("각 text는 15~45자로 작성한다")
        assertThat(prompt.system).contains("응답은 RecordFeedbackContent JSON schema만 따른다")
        assertThat(prompt.system).contains("goodPoint", "nextActions", "basis", "text", "evidenceRefs")
        assertThat(prompt.system).doesNotContain(
            "summary",
            "riskLevel",
            "diagnosis",
            "observations",
            "recommendations",
            "follow-up",
            "follow up",
        )
    }

    @Test
    fun `prompt includes target record live weather typed detail and evidence`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다."))
        )

        assertThat(prompt.user).contains("작물: 참당귀")
        assertThat(prompt.user).contains("작업유형: 관수")
        assertThat(prompt.user).contains("오전 흙 표면이 말라 보여 점적 관수함.")
        assertThat(prompt.user).contains("예보: 2026-07-04 강수 22.0mm")
        assertThat(prompt.user).contains("riskFlags=HEAVY_RAIN,HIGH_HUMIDITY")
        assertThat(prompt.user).contains("작업상세: irrigationAmount=NORMAL, irrigationMethod=DRIP")
        assertThat(prompt.user).contains("현재 날씨: 구름많음, 30C")
        assertThat(prompt.user).contains("허용 citationIds:")
        assertThat(prompt.user).contains("record:10000000-0000-0000-0000-000000000004 : 대상 영농기록 context")
        assertThat(prompt.user).contains("weather:current : 현재 날씨 context")
        assertThat(prompt.user).contains("weather:2026-07-04 : 예보 날씨 context")
        assertThat(prompt.user).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
        assertThat(prompt.user).contains("영농 경력: 1")
        assertThat(prompt.user).contains("경영 형태: NON_REGISTERED_FARMER")
        assertThat(prompt.user).contains("최저 21.4C")
        assertThat(prompt.user).doesNotContain("최근 기록", "작물주기", "작업 횟수")
    }

    @Test
    fun `prompt excludes blank evidence id from official evidence and allowed citations`() {
        val prompt = builder.build(
            context = readFixture("today-record-feedback-watering.json"),
            queries = listOf(RecordFeedbackRetrievalQuery("참당귀 물주기 재배 관리 약용작물", "crop_work_type")),
            evidence = listOf(
                RecordFeedbackEvidence("   ", "공백 ID 문서", 7, "공백 ID 근거는 prompt citation으로 쓰면 안 된다."),
                RecordFeedbackEvidence("doc-1", "농업기술길잡이 007 약용작물", 123, "관수 후 토양 상태를 확인한다.")
            )
        )

        val allowedCitationSection = prompt.user.substringAfter("허용 citationIds:")
            .substringBefore("공식문서 근거:")
        val officialEvidenceSection = prompt.user.substringAfter("공식문서 근거:")

        assertThat(allowedCitationSection).contains("doc-1 : 농업기술길잡이 007 약용작물")
        assertThat(allowedCitationSection).doesNotContain("공백 ID 문서")
        assertThat(officialEvidenceSection).contains("[doc-1] 농업기술길잡이 007 약용작물 p.123")
        assertThat(officialEvidenceSection).doesNotContain("공백 ID 문서")
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
