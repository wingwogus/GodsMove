package com.godsmove.application.coaching.rag

import java.time.LocalDate
import java.util.UUID

data class RagRetrievalFilter(
    val memberId: UUID,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workTypeId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null
)

interface RagIndexRepository {
    fun retrieve(
        embedding: List<Double>,
        filters: RagRetrievalFilter,
        topK: Int
    ): List<RagEvidenceChunk>
}
