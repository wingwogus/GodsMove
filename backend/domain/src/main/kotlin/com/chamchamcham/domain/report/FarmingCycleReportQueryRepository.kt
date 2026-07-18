package com.chamchamcham.domain.report

import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface FarmingCycleReportQueryRepository {
    data class Cursor(
        val endsAt: LocalDateTime,
        val finalHarvestRecordId: UUID,
    )

    data class SearchCondition(
        val memberId: UUID,
        val farmIds: Set<UUID> = emptySet(),
        val cropIds: Set<UUID> = emptySet(),
        val cursor: Cursor?,
        val size: Int,
    )

    data class SearchResult(
        val rows: List<FarmingCycleReport>,
    )

    data class WorkItemCursor(
        val lastWorkedOn: LocalDate?,
        val status: FarmingCycleReportStatus,
        val sortAt: LocalDateTime,
        val reportId: UUID,
        val workType: WorkType,
    )

    data class WorkItemSearchCondition(
        val memberId: UUID,
        val farmIds: Set<UUID> = emptySet(),
        val cropIds: Set<UUID> = emptySet(),
        val workType: WorkType?,
        val cursor: WorkItemCursor?,
        val size: Int,
    )

    data class WorkItem(
        val reportId: UUID,
        val status: FarmingCycleReportStatus,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val finalHarvestRecordId: UUID?,
        val workType: WorkType,
        val recordCount: Int,
        val lastWorkedOn: LocalDate?,
    ) {
        val sortAt: LocalDateTime
            get() = endsAt ?: startsAt
    }

    data class WorkItemSearchResult(
        val rows: List<WorkItem>,
    )

    fun searchCompleted(condition: SearchCondition): SearchResult

    fun searchWorkItems(condition: WorkItemSearchCondition): WorkItemSearchResult

    fun findPreviousCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        endsAt: LocalDateTime,
        finalHarvestRecordId: UUID,
    ): FarmingCycleReport?
}
