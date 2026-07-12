package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RecordFeedbackContextValidatorTest {
    @Test
    fun `valid context passes through deduplicated warnings`() {
        val warnings = RecordFeedbackContextValidator.requireValid(
            validContext().copy(warnings = listOf("weather_location_unavailable", "weather_location_unavailable")),
        )

        assertThat(warnings).containsExactly("weather_location_unavailable")
    }

    @Test
    fun `invalid schema version fails as invalid context`() {
        assertThatThrownBy {
            RecordFeedbackContextValidator.requireValid(
                validContext().copy(schemaVersion = "record-feedback-context.v1"),
            )
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INVALID_CONTEXT)
        }
    }

    @Test
    fun `blank record memo is an error because record feedback needs user observation`() {
        val context = validContext().copy(
            record = validContext().record.copy(memo = " ")
        )

        assertThatThrownBy { RecordFeedbackContextValidator.requireValid(context) }
            .isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INVALID_CONTEXT)
            }
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
