package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class RecordFeedbackContextTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `watering fixture deserializes into stable v2 context contract`() {
        val context = readFixture("today-record-feedback-watering.json")

        assertThat(context.schemaVersion).isEqualTo("record-feedback-context.v2")
        assertThat(context.crop.name).isEqualTo("참당귀")
        assertThat(context.crop.usePartCategory).isEqualTo(CropUsePartCategory.ROOT_BARK)
        assertThat(context.record.workType).isEqualTo(WorkType.WATERING)
        assertThat(context.record.detail).isEqualTo(WateringFeedbackDetail(IrrigationAmount.NORMAL, IrrigationMethod.DRIP))
        assertThat(context.weather?.forecastDays).hasSize(3)
        assertThat(context.weather?.forecastDays?.first()?.date.toString()).isEqualTo("2026-07-04")
        assertThat(context.weather?.forecastDays?.first()?.riskFlags).contains("HEAVY_RAIN")
        assertThat(RecordFeedbackContext::class.java.declaredFields.map { it.name })
            .doesNotContain("recentRecords", "workTypeStats", "cropCycle")
    }

    @Test
    fun `reduced weather fixture keeps weather nullable and warning explicit`() {
        val context = readFixture("today-record-feedback-no-cycle.json")

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_location_unavailable")
        assertThat(context.record.workType).isEqualTo(WorkType.WEEDING)
        assertThat(context.record.detail).isEqualTo(WeedingFeedbackDetail(WeedingMethod.HAND))
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }
}
