package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredResultTest {
    @Test
    fun `insufficient evidence result uses unknown risk and low confidence`() {
        val result = CoachingStructuredResult.insufficientEvidence("자료가 부족합니다.")

        assertThat(result.summary).isEqualTo("자료가 부족합니다.")
        assertThat(result.riskLevel).isEqualTo(CoachingRiskLevel.UNKNOWN)
        assertThat(result.confidence).isEqualTo(0.0)
        assertThat(result.recommendations).isEmpty()
        assertThat(result.followUpQuestions).isNotEmpty()
    }
}
