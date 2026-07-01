package com.godsmove.api.coaching.dto

import com.godsmove.application.coaching.rag.CoachingRagResult
import com.godsmove.application.coaching.rag.RagAuditStatus
import com.godsmove.application.coaching.rag.RagSourceType

object CoachingRagResponses {
    data class QueryResponse(
        val answer: String,
        val citations: List<CitationResponse>,
        val audit: AuditResponse,
        val model: ModelResponse
    ) {
        companion object {
            fun from(result: CoachingRagResult): QueryResponse {
                return QueryResponse(
                    answer = result.answer,
                    citations = result.citations.map {
                        CitationResponse(
                            chunkId = it.chunkId,
                            sourceType = it.sourceType,
                            sourceId = it.sourceId,
                            label = it.label,
                            similarityScore = it.similarityScore
                        )
                    },
                    audit = AuditResponse(result.audit.status, result.audit.warnings),
                    model = ModelResponse(result.model.embedding, result.model.chat)
                )
            }
        }
    }

    data class CitationResponse(
        val chunkId: String,
        val sourceType: RagSourceType,
        val sourceId: String,
        val label: String,
        val similarityScore: Double
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
