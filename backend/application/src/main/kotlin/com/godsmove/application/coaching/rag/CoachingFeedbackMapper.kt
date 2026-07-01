package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component
import java.math.BigDecimal

data class CoachingFeedbackJsonPayload(
    val structuredResult: Map<String, Any?>,
    val citations: List<Map<String, Any?>>,
    val confidenceScore: BigDecimal
)

@Component
class CoachingFeedbackMapper {
    fun toPayload(command: CoachingRagCommand, result: CoachingStructuredResult): CoachingFeedbackJsonPayload {
        return CoachingFeedbackJsonPayload(
            structuredResult = toStructuredMap(command, result),
            citations = result.citations.map {
                mapOf(
                    "chunkId" to it.chunkId,
                    "label" to it.label,
                    "sourceType" to it.sourceType.name
                )
            },
            confidenceScore = BigDecimal.valueOf(result.confidence).setScale(4)
        )
    }

    fun toStructuredMap(command: CoachingRagCommand, result: CoachingStructuredResult): Map<String, Any?> {
        return mapOf(
            "question" to command.question,
            "mode" to command.mode.name,
            "summary" to result.summary,
            "riskLevel" to result.riskLevel.name,
            "confidence" to result.confidence,
            "observations" to result.observations.map {
                mapOf("title" to it.title, "detail" to it.detail, "citationIds" to it.citationIds)
            },
            "diagnosis" to result.diagnosis,
            "recommendations" to result.recommendations.map {
                mapOf(
                    "priority" to it.priority.name,
                    "action" to it.action,
                    "reason" to it.reason,
                    "caution" to it.caution,
                    "citationIds" to it.citationIds
                )
            },
            "nextActions" to result.nextActions.map {
                mapOf("due" to it.due.name, "action" to it.action, "citationIds" to it.citationIds)
            },
            "followUpQuestions" to result.followUpQuestions,
            "citations" to result.citations.map {
                mapOf("chunkId" to it.chunkId, "label" to it.label, "sourceType" to it.sourceType.name)
            }
        )
    }
}
