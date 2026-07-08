package com.chamchamcham.domain.farming

import java.time.LocalDateTime
import java.util.UUID

interface FarmingRecordQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val cropId: UUID?,
        val workType: WorkType?,
        val workedAtFrom: LocalDateTime?,
        val workedAtTo: LocalDateTime?,
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val workedAt: LocalDateTime,
        val id: UUID
    )

    data class Row(
        val record: FarmingRecord,
        val thumbnailUrl: String?
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
