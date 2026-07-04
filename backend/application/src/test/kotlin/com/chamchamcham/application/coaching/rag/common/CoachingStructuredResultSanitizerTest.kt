package com.chamchamcham.application.coaching.rag.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredResultSanitizerTest {
    private val sanitizer = CoachingStructuredResultSanitizer()

    @Test
    fun `removes uncited recommendations and next actions`() {
        val result = baseResult().copy(
            recommendations = listOf(
                recommendation("근거 있는 조언", listOf("doc-1")),
                recommendation("무근거 조언", emptyList())
            ),
            nextActions = listOf(
                nextAction("근거 있는 행동", listOf("doc-1")),
                nextAction("무근거 행동", emptyList())
            )
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized.recommendations.map { it.action }).containsExactly("근거 있는 조언")
        assertThat(sanitized.nextActions.map { it.action }).containsExactly("근거 있는 행동")
        assertThat(sanitized.limitations).contains(CoachingStructuredResultSanitizer.SANITIZED_LIMITATION)
    }

    @Test
    fun `strips unknown citation ids and drops items left without citations`() {
        val result = baseResult().copy(
            observations = listOf(CoachingObservation("관찰", "내용", listOf("doc-1", "ghost"))),
            recommendations = listOf(
                recommendation("혼합 인용 조언", listOf("doc-1", "ghost")),
                recommendation("환각 인용 조언", listOf("ghost"))
            ),
            citations = listOf(
                CoachingCitationRef("doc-1", "실제 문서", RagSourceType.TECH_DOCUMENT),
                CoachingCitationRef("ghost", "환각 문서", RagSourceType.TECH_DOCUMENT)
            )
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized.observations.single().citationIds).containsExactly("doc-1")
        assertThat(sanitized.recommendations.map { it.action }).containsExactly("혼합 인용 조언")
        assertThat(sanitized.recommendations.single().citationIds).containsExactly("doc-1")
        assertThat(sanitized.citations.map { it.chunkId }).containsExactly("doc-1")
    }

    @Test
    fun `returns same instance when nothing to sanitize`() {
        val result = baseResult().copy(
            recommendations = listOf(recommendation("근거 있는 조언", listOf("doc-1")))
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized).isSameAs(result)
    }

    private fun baseResult(): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.MEDIUM,
            confidence = 0.7,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )
    }

    private fun recommendation(action: String, citationIds: List<String>): CoachingRecommendation {
        return CoachingRecommendation(CoachingPriority.MEDIUM, action, "이유", null, citationIds)
    }

    private fun nextAction(action: String, citationIds: List<String>): CoachingNextAction {
        return CoachingNextAction(CoachingActionDue.TODAY, action, citationIds)
    }
}
