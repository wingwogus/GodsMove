package com.chamchamcham.application.coaching.rag.common

import org.springframework.stereotype.Component

@Component
class CoachingStructuredResultSanitizer {
    fun sanitize(
        result: CoachingStructuredResult,
        allowedCitationIds: Set<String>
    ): CoachingStructuredResult {
        val observations = result.observations.map {
            it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains))
        }
        val recommendations = result.recommendations
            .map { it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains)) }
            .filter { it.citationIds.isNotEmpty() }
        val nextActions = result.nextActions
            .map { it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains)) }
            .filter { it.citationIds.isNotEmpty() }
        val citations = result.citations.filter { it.chunkId in allowedCitationIds }

        val unchanged = observations == result.observations &&
            recommendations == result.recommendations &&
            nextActions == result.nextActions &&
            citations == result.citations
        if (unchanged) {
            return result
        }

        return result.copy(
            observations = observations,
            recommendations = recommendations,
            nextActions = nextActions,
            citations = citations,
            limitations = result.limitations + SANITIZED_LIMITATION
        )
    }

    companion object {
        const val SANITIZED_LIMITATION = "일부 조언이 근거 검증을 통과하지 못해 제외되었습니다."
    }
}
