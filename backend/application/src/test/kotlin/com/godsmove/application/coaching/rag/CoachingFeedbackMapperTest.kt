package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class CoachingFeedbackMapperTest {
    @Test
    fun `structured result is converted to json maps`() {
        val mapper = CoachingFeedbackMapper()
        val result = CoachingStructuredResult.insufficientEvidence("부족")
        val command = CoachingRagCommand(
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
            mode = CoachingMode.REPORT_MANUAL,
            question = "리포트"
        )

        val mapped = mapper.toStructuredMap(command, result)

        assertThat(mapped["summary"]).isEqualTo("부족")
        assertThat(mapped["riskLevel"]).isEqualTo("UNKNOWN")
        assertThat(mapped["question"]).isEqualTo("리포트")
    }

    @Test
    fun `payload includes structured result citations and scaled confidence`() {
        val mapper = CoachingFeedbackMapper()
        val command = CoachingRagCommand(
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
            mode = CoachingMode.RECORD_AUTO,
            question = "자동 코칭"
        )
        val result = CoachingStructuredResult(
            summary = "확인 필요",
            riskLevel = CoachingRiskLevel.MEDIUM,
            confidence = 0.8,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = listOf(CoachingCitationRef("chunk-1", "영농일지", RagSourceType.FARMING_RECORD))
        )

        val payload = mapper.toPayload(command, result)

        assertThat(payload.structuredResult["mode"]).isEqualTo("RECORD_AUTO")
        assertThat(payload.citations).containsExactly(
            mapOf("chunkId" to "chunk-1", "label" to "영농일지", "sourceType" to "FARMING_RECORD")
        )
        assertThat(payload.confidenceScore).isEqualByComparingTo(BigDecimal("0.8000"))
    }
}
