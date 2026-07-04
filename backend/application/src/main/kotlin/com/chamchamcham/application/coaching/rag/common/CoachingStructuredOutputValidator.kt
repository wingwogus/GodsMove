package com.chamchamcham.application.coaching.rag.common

import org.springframework.stereotype.Component

@Component
class CoachingStructuredOutputValidator {
    fun validate(result: CoachingStructuredResult, allowedCitationIds: Set<String>): RagAuditResult {
        val warnings = mutableListOf<String>()

        if (result.confidence !in 0.0..1.0) {
            warnings += "invalid_confidence"
        }

        val citationIds = buildList {
            result.observations.forEach { addAll(it.citationIds) }
            result.recommendations.forEach { addAll(it.citationIds) }
            result.nextActions.forEach { addAll(it.citationIds) }
            result.citations.forEach { add(it.chunkId) }
        }

        result.recommendations
            .filter { it.citationIds.isEmpty() }
            .forEach { warnings += "recommendation_without_citation:${it.action}" }

        result.nextActions
            .filter { it.citationIds.isEmpty() }
            .forEach { warnings += "next_action_without_citation:${it.action}" }

        citationIds.distinct().forEach { citationId ->
            if (citationId !in allowedCitationIds) {
                warnings += "unknown_citation:$citationId"
            }
        }

        val failure = warnings.any {
            it == "invalid_confidence" ||
                it.startsWith("unknown_citation:") ||
                it.startsWith("recommendation_without_citation:") ||
                it.startsWith("next_action_without_citation:")
        }

        return RagAuditResult(
            status = if (failure) {
                RagAuditStatus.FAIL
            } else if (warnings.isEmpty()) {
                RagAuditStatus.PASS
            } else {
                RagAuditStatus.WARN
            },
            warnings = warnings.distinct(),
            citations = citationIds.distinct()
        )
    }
}
