package com.chamchamcham.application.coaching.rag.record

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackRetrievalQueryPlannerTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val planner = RecordFeedbackRetrievalQueryPlanner()

    @Test
    fun `watering context creates crop work type cycle and dry weather queries`() {
        val queries = planner.plan(readFixture("today-record-feedback-watering.json")).map { it.query }

        assertThat(queries).contains(
            "참당귀 물주기 재배 관리 약용작물",
            "참당귀 76일차 생육 관리",
            "참당귀 고온 건조 관수 병해충",
            "참당귀 강우 예보 배수 과습 병해충"
        )
    }

    @Test
    fun `pest control context includes target disease query`() {
        val queries = planner.plan(readFixture("today-record-feedback-pest-control.json")).map { it.query }

        assertThat(queries).contains(
            "인삼 병해충 방제 재배 관리 약용작물",
            "인삼 점무늬병 방제"
        )
    }

    @Test
    fun `harvest context includes medicinal use part harvest query`() {
        val queries = planner.plan(readFixture("today-record-feedback-harvest.json")).map { it.query }

        assertThat(queries).contains(
            "오미자 수확 재배 관리 약용작물",
            "약용작물 열매/과실 수확 적기 오미자"
        )
    }

    @Test
    fun `no cycle context does not create days after planting query`() {
        val queries = planner.plan(readFixture("today-record-feedback-no-cycle.json")).map { it.query }

        assertThat(queries).contains("참당귀 제초 재배 관리 약용작물")
        assertThat(queries).noneMatch { it.contains("일차 생육 관리") }
    }

    @Test
    fun `memo text creates second priority query and keeps forecast query`() {
        val queries = planner.plan(readFixture("today-record-feedback-watering.json"))

        assertThat(queries[1].reason).isEqualTo("memo_text")
        assertThat(queries[1].query).isEqualTo("참당귀 오전 흙 표면이 말라 보여 점적 관수함.")
        assertThat(queries.map { it.query }).contains("참당귀 강우 예보 배수 과습 병해충")
    }

    @Test
    fun `blank memo does not create memo query`() {
        val base = readFixture("today-record-feedback-watering.json")
        val context = base.copy(targetRecord = base.targetRecord.copy(memo = "  "))

        assertThat(planner.plan(context).map { it.reason }).doesNotContain("memo_text")
    }

    @Test
    fun `long memo is truncated to 120 chars`() {
        val base = readFixture("today-record-feedback-watering.json")
        val context = base.copy(targetRecord = base.targetRecord.copy(memo = "가".repeat(200)))

        val memoQuery = planner.plan(context).first { it.reason == "memo_text" }
        assertThat(memoQuery.query).isEqualTo("참당귀 " + "가".repeat(120))
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }
}
