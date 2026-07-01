package com.godsmove.application.coaching.rag

import java.util.UUID

enum class RagSourceType {
    TECH_DOCUMENT,
    FARMING_RECORD
}

data class RagEvidenceChunk(
    val id: UUID,
    val sourceType: RagSourceType,
    val sourceId: String,
    val content: String,
    val label: String,
    val similarityScore: Double
)

data class RagCitation(
    val chunkId: String,
    val sourceType: RagSourceType,
    val sourceId: String,
    val label: String,
    val similarityScore: Double
)
