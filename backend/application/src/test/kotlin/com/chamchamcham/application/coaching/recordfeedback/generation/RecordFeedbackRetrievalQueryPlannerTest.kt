package com.chamchamcham.application.coaching.recordfeedback.generation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.math.BigDecimal

class RecordFeedbackRetrievalQueryPlannerTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val planner = RecordFeedbackRetrievalQueryPlanner()

    @Test
    fun `watering context creates crop work type memo and grounded weather queries without cycle query`() {
        val queries = planner.plan(readFixture("today-record-feedback-watering.json")).map { it.query }

        assertThat(queries).contains(
            "참당귀 관수 재배 관리 약용작물",
            "참당귀 오전 흙 표면이 말라 보여 점적 관수함.",
            "참당귀 고온 생육 관리",
            "참당귀 강우 예보 배수 과습 병해충"
        )
        assertThat(queries).noneMatch { it.contains("일차 생육 관리") }
    }

    @Test
    fun `pest control context includes target disease query from typed detail`() {
        val queries = planner.plan(readFixture("today-record-feedback-pest-control.json")).map { it.query }

        assertThat(queries).contains(
            "인삼 병해충 관리 재배 관리 약용작물",
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
    fun `harvest context without recorded medicinal part keeps only generic harvest timing query`() {
        val base = readFixture("today-record-feedback-harvest.json")
        val detail = base.record.detail as HarvestFeedbackDetail
        val context = base.copy(
            record = base.record.copy(detail = detail.copy(medicinalPart = null)),
        )

        val queries = planner.plan(context).map { it.query }

        assertThat(queries).contains("약용작물 수확 적기 오미자")
        assertThat(queries).noneMatch { it.contains("열매/과실") }
    }

    @Test
    fun `reduced weather context does not create weather query`() {
        val queries = planner.plan(readFixture("today-record-feedback-no-cycle.json"))

        assertThat(queries.map { it.query }).contains("참당귀 잡초 관리 재배 관리 약용작물")
        assertThat(queries.map { it.reason }).doesNotContain(
            "rain_wet_weather",
            "hot_weather",
            "forecast_rain_wet_weather",
            "forecast_dry_weather",
            "forecast_hot_weather",
        )
    }

    @Test
    fun `blank memo does not create memo query`() {
        val base = readFixture("today-record-feedback-watering.json")
        val context = base.copy(record = base.record.copy(memo = "  "))

        assertThat(planner.plan(context).map { it.reason }).doesNotContain("memo_text")
    }

    @Test
    fun `long memo is truncated to 120 chars`() {
        val base = readFixture("today-record-feedback-watering.json")
        val context = base.copy(record = base.record.copy(memo = "가".repeat(200)))

        val memoQuery = planner.plan(context).first { it.reason == "memo_text" }
        assertThat(memoQuery.query).isEqualTo("참당귀 " + "가".repeat(120))
    }

    @Test
    fun `wet forecast takes priority over hot weather query`() {
        val base = readFixture("today-record-feedback-watering.json")
        val weather = base.weather ?: error("fixture must contain weather")
        val context = base.copy(
            weather = weather.copy(
                current = weather.current.copy(temperatureC = 33),
                forecastDays = listOf(
                    weather.forecastDays.first().copy(
                        rainfallMm = BigDecimal("40.0"),
                        riskFlags = listOf("RAIN")
                    )
                )
            )
        )

        val reasons = planner.plan(context).map { it.reason }

        assertThat(reasons).contains("rain_wet_weather")
        assertThat(reasons).doesNotContain("hot_weather")
    }

    @Test
    fun `ordinary current weather without forecast risks does not trigger weather query`() {
        val base = readFixture("today-record-feedback-watering.json")
        val weather = base.weather ?: error("fixture must contain weather")
        val context = base.copy(
            weather = weather.copy(
                current = weather.current.copy(temperatureC = 24),
                forecastDays = emptyList()
            )
        )

        val reasons = planner.plan(context).map { it.reason }

        assertThat(reasons).doesNotContain("hot_weather")
        assertThat(reasons).doesNotContain("rain_wet_weather")
    }

    @Test
    fun `hot forecast without dry evidence does not retrieve dry irrigation guidance`() {
        val base = readFixture("today-record-feedback-watering.json")
        val weather = base.weather ?: error("fixture must contain weather")
        val context = base.copy(
            weather = weather.copy(
                forecastDays = listOf(
                    weather.forecastDays.first().copy(
                        rainfallMm = null,
                        rainProbabilityPct = 20,
                        maxTemperatureC = BigDecimal("32"),
                        humidityPct = BigDecimal("50"),
                        riskFlags = listOf("HOT"),
                    )
                )
            )
        )

        val queries = planner.plan(context)

        assertThat(queries.map { it.query }).contains("참당귀 고온 예보 생육 관리")
        assertThat(queries.map { it.query }).noneMatch { it.contains("건조") || it.contains("관수 관리") }
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
