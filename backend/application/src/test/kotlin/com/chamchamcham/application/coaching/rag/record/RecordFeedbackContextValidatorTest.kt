package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RecordFeedbackContextValidatorTest {
    private val validator = RecordFeedbackContextValidator()

    @Test
    fun `valid context has no errors`() {
        val result = validator.validate(validContext())

        assertThat(result.errors).isEmpty()
        assertThat(result.warnings).isEmpty()
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `invalid schema version is an error`() {
        val result = validator.validate(validContext().copy(schemaVersion = "record-feedback-context.v1"))

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).containsExactly("invalid_schema_version")
    }

    @Test
    fun `assembler warnings pass through validation warnings`() {
        val result = validator.validate(validContext().copy(warnings = listOf("weather_location_unavailable")))

        assertThat(result.isValid).isTrue()
        assertThat(result.warnings).containsExactly("weather_location_unavailable")
    }

    @Test
    fun `blank record memo is an error because record feedback needs user observation`() {
        val context = validContext().copy(
            record = validContext().record.copy(memo = " ")
        )

        val result = validator.validate(context)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("record_memo_blank")
    }

    private fun validContext(): RecordFeedbackContext {
        return RecordFeedbackContext(
            member = RecordFeedbackMemberContext(
                memberId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                experienceLevel = 1,
                managementType = ManagementType.NON_REGISTERED_FARMER,
            ),
            farm = RecordFeedbackFarmContext(
                farmId = UUID.fromString("10000000-0000-0000-0000-000000000002"),
                name = "청년약초밭",
                roadAddress = "강원특별자치도 평창군",
                latitude = 37.1,
                longitude = 128.2,
            ),
            crop = RecordFeedbackCropContext(
                cropId = UUID.fromString("10000000-0000-0000-0000-000000000003"),
                name = "참당귀",
                usePartCategory = CropUsePartCategory.ROOT_BARK,
            ),
            record = RecordFeedbackRecordContext(
                recordId = UUID.fromString("10000000-0000-0000-0000-000000000004"),
                sourceRevision = 1,
                workedAt = LocalDateTime.parse("2026-07-03T08:30:00"),
                workType = WorkType.WATERING,
                detail = WateringFeedbackDetail(IrrigationAmount.NORMAL, IrrigationMethod.DRIP),
                recordedWeatherCondition = "맑음",
                recordedTemperatureC = 30,
                memo = "흙 표면이 말라 관수함",
                photoCount = 1,
            ),
            weather = null,
        )
    }
}
