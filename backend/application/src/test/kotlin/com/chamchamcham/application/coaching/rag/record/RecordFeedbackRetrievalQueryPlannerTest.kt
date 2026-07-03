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
            "참당귀 고온 건조 관수 병해충"
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

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }
}
