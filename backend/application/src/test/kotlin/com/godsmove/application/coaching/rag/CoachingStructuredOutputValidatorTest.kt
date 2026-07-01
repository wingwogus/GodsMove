package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredOutputValidatorTest {
    private val validator = CoachingStructuredOutputValidator()

    @Test
    fun `invalid confidence fails audit`() {
        val result = CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.MEDIUM,
            confidence = 1.4,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )

        val audit = validator.validate(result, emptySet())

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("invalid_confidence")
    }

    @Test
    fun `unknown citation fails audit`() {
        val result = CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.LOW,
            confidence = 0.7,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.HIGH, "관수 중단", "과습", null, listOf("missing"))
            ),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )

        val audit = validator.validate(result, setOf("known"))

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("unknown_citation:missing")
    }

    @Test
    fun `recommendation without citation fails audit for known risk`() {
        val result = CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.HIGH,
            confidence = 0.8,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.HIGH, "배수로 확인", "과습 위험", null, emptyList())
            ),
            nextActions = listOf(CoachingNextAction(CoachingActionDue.TODAY, "토양 상태 재점검", emptyList())),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )

        val audit = validator.validate(result, emptySet())

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains(
            "recommendation_without_citation:배수로 확인",
            "next_action_without_citation:토양 상태 재점검"
        )
    }
}
