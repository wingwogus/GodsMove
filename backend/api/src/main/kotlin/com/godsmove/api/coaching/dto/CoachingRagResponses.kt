package com.godsmove.api.coaching.dto

import com.godsmove.application.coaching.rag.CoachingActionDue
import com.godsmove.application.coaching.rag.CoachingPriority
import com.godsmove.application.coaching.rag.CoachingRagResult
import com.godsmove.application.coaching.rag.CoachingRiskLevel
import com.godsmove.application.coaching.rag.RagAuditStatus
import com.godsmove.application.coaching.rag.RagSourceType
import java.util.UUID

object CoachingRagResponses {
    data class QueryResponse(
        val result: StructuredResultResponse,
        val audit: AuditResponse,
        val model: ModelResponse,
        val savedFeedbackId: UUID?
    ) {
        companion object {
            fun from(result: CoachingRagResult): QueryResponse {
                return QueryResponse(
                    result = StructuredResultResponse.from(result.result),
                    audit = AuditResponse(result.audit.status, result.audit.warnings),
                    model = ModelResponse(result.model.embedding, result.model.chat),
                    savedFeedbackId = result.savedFeedbackId
                )
            }
        }
    }

    data class StructuredResultResponse(
        val summary: String,
        val riskLevel: CoachingRiskLevel,
        val confidence: Double,
        val observations: List<ObservationResponse>,
        val diagnosis: String,
        val recommendations: List<RecommendationResponse>,
        val nextActions: List<NextActionResponse>,
        val followUpQuestions: List<String>,
        val citations: List<CitationResponse>
    ) {
        companion object {
            fun from(result: com.godsmove.application.coaching.rag.CoachingStructuredResult): StructuredResultResponse {
                return StructuredResultResponse(
                    summary = result.summary,
                    riskLevel = result.riskLevel,
                    confidence = result.confidence,
                    observations = result.observations.map { ObservationResponse(it.title, it.detail, it.citationIds) },
                    diagnosis = result.diagnosis,
                    recommendations = result.recommendations.map {
                        RecommendationResponse(it.priority, it.action, it.reason, it.caution, it.citationIds)
                    },
                    nextActions = result.nextActions.map { NextActionResponse(it.due, it.action, it.citationIds) },
                    followUpQuestions = result.followUpQuestions,
                    citations = result.citations.map { CitationResponse(it.chunkId, it.label, it.sourceType) }
                )
            }
        }
    }

    data class ObservationResponse(
        val title: String,
        val detail: String,
        val citationIds: List<String>
    )

    data class RecommendationResponse(
        val priority: CoachingPriority,
        val action: String,
        val reason: String,
        val caution: String?,
        val citationIds: List<String>
    )

    data class NextActionResponse(
        val due: CoachingActionDue,
        val action: String,
        val citationIds: List<String>
    )

    data class CitationResponse(
        val chunkId: String,
        val label: String,
        val sourceType: RagSourceType
    )

    data class AuditResponse(
        val status: RagAuditStatus,
        val warnings: List<String>
    )

    data class ModelResponse(
        val embedding: String,
        val chat: String
    )
}
