package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayRecordFeedbackContextValidatorTest {
    private val validator = TodayRecordFeedbackContextValidator()

    @Test
    fun `valid context has no errors`() {
        val result = validator.validate(validContext())

        assertThat(result.errors).isEmpty()
        assertThat(result.warnings).isEmpty()
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `invalid schema version is an error`() {
        val result = validator.validate(validContext().copy(schemaVersion = "record-feedback-context.v2"))

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).containsExactly("invalid_schema_version")
    }

    @Test
    fun `missing crop cycle is a warning not an error`() {
        val result = validator.validate(validContext().copy(cropCycle = null))

        assertThat(result.isValid).isTrue()
        assertThat(result.warnings).containsExactly("crop_cycle_unknown")
    }

    @Test
    fun `blank target memo is an error because record feedback needs user observation`() {
        val context = validContext().copy(
            targetRecord = validContext().targetRecord.copy(memo = " ")
        )

        val result = validator.validate(context)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("target_record_memo_blank")
    }

    private fun validContext(): TodayRecordFeedbackContext {
        return TodayRecordFeedbackContext(
            schemaVersion = TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            feedbackRequestId = "feedback-1",
            mode = CoachingMode.RECORD_AUTO,
            member = RecordFeedbackMemberContext("member-1", 1, "NON_REGISTERED_FARMER"),
            farm = RecordFeedbackFarmContext("farm-1", "청년약초밭", "강원특별자치도 평창군", "FARM_ADDRESS"),
            crop = RecordFeedbackCropContext("crop-1", "참당귀", CropUsePartCategory.ROOT_BARK),
            cropCycle = RecordFeedbackCropCycleContext(
                cycleId = "cycle-1",
                startedRecordId = "record-start",
                startedOn = LocalDate.parse("2026-04-18"),
                daysAfterPlanting = 76,
                startBasis = "PLANTING_RECORD"
            ),
            targetRecord = RecordFeedbackTargetRecordContext(
                recordId = "record-1",
                recordedOn = LocalDate.parse("2026-07-03"),
                workType = TodayRecordWorkType.WATERING,
                fields = mapOf("waterAmountScale" to "보통"),
                memo = "흙 표면이 말라 관수함",
                hasPhoto = true,
                photoCount = 1
            ),
            weather = RecordFeedbackWeatherContext(
                recordDay = RecordFeedbackRecordDayWeather(24.8, 30.1, 19.9, 0.0, 71.0),
                recent7Days = RecordFeedbackRecentWeatherSummary(4.5, 2, 5),
                source = "SERVER_WEATHER_SNAPSHOT"
            )
        )
    }
}
