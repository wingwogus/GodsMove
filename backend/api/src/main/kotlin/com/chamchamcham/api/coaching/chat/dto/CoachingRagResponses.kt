package com.chamchamcham.api.coaching.chat.dto

import com.chamchamcham.application.coaching.chat.CoachingActionDue
import com.chamchamcham.application.coaching.chat.CoachingStructuredResult
import com.chamchamcham.application.coaching.chat.CoachingPriority
import com.chamchamcham.application.coaching.chat.CoachingRecordQuality
import com.chamchamcham.application.coaching.chat.CoachingRecordQualityScore
import com.chamchamcham.application.coaching.chat.CoachingRiskLevel
import com.chamchamcham.application.coaching.common.RagAuditStatus
import com.chamchamcham.application.coaching.common.RagSourceType
import com.chamchamcham.application.coaching.chat.CoachingRagResult
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
        val citations: List<CitationResponse>,
        val recordQuality: RecordQualityResponse,
        val limitations: List<String>
    ) {
        companion object {
            fun from(result: CoachingStructuredResult): StructuredResultResponse {
                val citationLabelsById = result.citations
                    .mapIndexed { index, citation ->
                        citation.chunkId to "${displayLabel(index)}: ${citation.label}"
                    }
                    .toMap()

                return StructuredResultResponse(
                    summary = result.summary,
                    riskLevel = result.riskLevel,
                    confidence = result.confidence,
                    observations = result.observations.map {
                        ObservationResponse(
                            title = it.title,
                            detail = it.detail,
                            citationIds = it.citationIds,
                            citationLabels = it.citationIds.toCitationLabels(citationLabelsById)
                        )
                    },
                    diagnosis = result.diagnosis,
                    recommendations = result.recommendations.map {
                        RecommendationResponse(
                            priority = it.priority,
                            action = it.action,
                            reason = it.reason,
                            caution = it.caution,
                            citationIds = it.citationIds,
                            citationLabels = it.citationIds.toCitationLabels(citationLabelsById)
                        )
                    },
                    nextActions = result.nextActions.map {
                        NextActionResponse(
                            due = it.due,
                            action = it.action,
                            citationIds = it.citationIds,
                            citationLabels = it.citationIds.toCitationLabels(citationLabelsById)
                        )
                    },
                    followUpQuestions = result.followUpQuestions,
                    citations = result.citations.mapIndexed { index, citation ->
                        CitationResponse(
                            chunkId = citation.chunkId,
                            label = citation.label,
                            sourceType = citation.sourceType,
                            displayLabel = displayLabel(index),
                            documentTitle = citation.documentTitle,
                            page = citation.page,
                            source = citation.source
                        )
                    },
                    recordQuality = RecordQualityResponse.from(result.recordQuality),
                    limitations = result.limitations
                )
            }

            private fun displayLabel(index: Int): String {
                return "근거 ${index + 1}"
            }

            private fun List<String>.toCitationLabels(citationLabelsById: Map<String, String>): List<String> {
                return map { citationLabelsById[it] ?: "근거 확인 필요" }
            }
        }
    }

    data class ObservationResponse(
        val title: String,
        val detail: String,
        val citationIds: List<String>,
        val citationLabels: List<String>
    )

    data class RecommendationResponse(
        val priority: CoachingPriority,
        val action: String,
        val reason: String,
        val caution: String?,
        val citationIds: List<String>,
        val citationLabels: List<String>
    )

    data class NextActionResponse(
        val due: CoachingActionDue,
        val action: String,
        val citationIds: List<String>,
        val citationLabels: List<String>
    )

    data class CitationResponse(
        val chunkId: String,
        val label: String,
        val sourceType: RagSourceType,
        val displayLabel: String,
        val documentTitle: String?,
        val page: Int?,
        val source: String?
    )

    data class RecordQualityResponse(
        val score: CoachingRecordQualityScore,
        val missingOrWeakFields: List<String>,
        val comment: String
    ) {
        companion object {
            fun from(recordQuality: CoachingRecordQuality): RecordQualityResponse {
                return RecordQualityResponse(
                    score = recordQuality.score,
                    missingOrWeakFields = recordQuality.missingOrWeakFields,
                    comment = recordQuality.comment
                )
            }
        }
    }

    data class AuditResponse(
        val status: RagAuditStatus,
        val warnings: List<String>
    )

    data class ModelResponse(
        val embedding: String,
        val chat: String
    )
}
