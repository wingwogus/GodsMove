package com.chamchamcham.domain.report

import java.time.LocalDateTime
import java.util.UUID

interface FarmingCycleReportQueryRepository {
    data class Cursor(
        val endsAt: LocalDateTime,
        val finalHarvestRecordId: UUID,
    )

    data class SearchCondition(
        val memberId: UUID,
        val farmId: UUID,
        val cropId: UUID,
        val cursor: Cursor?,
        val size: Int,
    )

    data class SearchResult(
        val rows: List<FarmingCycleReport>,
    )

    fun searchCompleted(condition: SearchCondition): SearchResult

    fun findLatestCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReport?

    fun findPreviousCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        endsAt: LocalDateTime,
        finalHarvestRecordId: UUID,
    ): FarmingCycleReport?
}
