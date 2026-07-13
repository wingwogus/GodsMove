package com.chamchamcham.application.coaching.chat

import com.chamchamcham.domain.coaching.chat.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class CoachingRetrievalFilterBuilderTest {
    private val builder = CoachingRetrievalFilterBuilder()

    @Test
    fun `build includes shared docs and current member records`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val cropId = UUID.fromString("20000000-0000-0000-0000-000000000042")

        val filter = builder.build(
            CoachingRagCommand(
                memberId = memberId,
                mode = CoachingMode.CHAT,
                question = "과습 위험?",
                cropId = cropId,
                periodStart = LocalDate.parse("2026-06-01"),
                periodEnd = LocalDate.parse("2026-06-30")
            )
        )

        assertThat(filter).contains("sourceType == 'TECH_DOCUMENT'")
        assertThat(filter).contains("memberId == '$memberId'")
        assertThat(filter).contains("cropId == '$cropId'")
        assertThat(filter).contains("workedAtEpochDay >= 20605")
        assertThat(filter).contains("workedAtEpochDay <= 20634")
    }
}
