package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class TodayRecordFeedbackContextTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `watering fixture deserializes into stable context contract`() {
        val context = readFixture("today-record-feedback-watering.json")

        assertThat(context.schemaVersion).isEqualTo("record-feedback-context.v1")
        assertThat(context.mode).isEqualTo(CoachingMode.RECORD_AUTO)
        assertThat(context.crop.name).isEqualTo("참당귀")
        assertThat(context.crop.usePartCategory).isEqualTo(CropUsePartCategory.ROOT_BARK)
        assertThat(context.cropCycle?.daysAfterPlanting).isEqualTo(76)
        assertThat(context.targetRecord.workType).isEqualTo(TodayRecordWorkType.WATERING)
        assertThat(context.targetRecord.fieldText("wateringMethod")).isEqualTo("점적")
        assertThat(context.weather.recent7Days.dryDaysCount).isEqualTo(5)
        assertThat(context.workTypeStats.cycleCounts[TodayRecordWorkType.WATERING]).isEqualTo(8)
    }

    @Test
    fun `no cycle fixture keeps crop cycle nullable for conservative feedback`() {
        val context = readFixture("today-record-feedback-no-cycle.json")

        assertThat(context.cropCycle).isNull()
        assertThat(context.targetRecord.workType).isEqualTo(TodayRecordWorkType.WEEDING)
        assertThat(context.recentRecords).isEmpty()
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }
}
