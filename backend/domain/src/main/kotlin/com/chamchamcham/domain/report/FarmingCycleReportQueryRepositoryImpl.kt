package com.chamchamcham.domain.report

import com.chamchamcham.domain.farming.WorkType
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Repository
class FarmingCycleReportQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : FarmingCycleReportQueryRepository {
    override fun searchCompleted(
        condition: FarmingCycleReportQueryRepository.SearchCondition,
    ): FarmingCycleReportQueryRepository.SearchResult {
        val where = completedScopeWhere(condition.farmIds, condition.cropIds)
        val params = completedScopeParams(condition.memberId, condition.farmIds, condition.cropIds)

        condition.cursor?.let { cursor ->
            where += cursorPredicate()
            params["cursorEndsAt"] = cursor.endsAt
            params["cursorFinalHarvestRecordId"] = cursor.finalHarvestRecordId
        }

        return FarmingCycleReportQueryRepository.SearchResult(
            rows = findCompleted(where, params, condition.size),
        )
    }

    override fun searchWorkItems(
        condition: FarmingCycleReportQueryRepository.WorkItemSearchCondition,
    ): FarmingCycleReportQueryRepository.WorkItemSearchResult {
        val where = workItemScopeWhere(condition.farmIds, condition.cropIds)
        val params = workItemScopeParams(condition.memberId, condition.farmIds, condition.cropIds)
        val query = entityManager.createQuery(
            """
            select r.id, r.status, r.farm.id, r.farm.name, r.crop.id, r.crop.name,
                   r.startsAt, r.endsAt, r.finalHarvestRecord.id, r.statistics
            from FarmingCycleReport r
            where ${where.joinToString(" and ")}
            order by r.startsAt desc, r.id desc
            """.trimIndent(),
            Array<Any>::class.java,
        )
        params.forEach(query::setParameter)

        val rows = query.resultList
            .map(::toWorkProjection)
            .flatMap { projection ->
                val workTypes = condition.workTypes.distinct().ifEmpty { WorkType.entries }
                workTypes.mapNotNull { workType -> projection.toWorkItem(workType) }
            }
            .sortedWith(workItemComparator)
            .filter { item -> condition.cursor?.let { item.isAfter(it) } ?: true }
            .take(condition.size)

        return FarmingCycleReportQueryRepository.WorkItemSearchResult(rows)
    }

    override fun findPreviousCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        endsAt: LocalDateTime,
        finalHarvestRecordId: UUID,
    ): FarmingCycleReport? {
        val where = completedScopeWhere(setOf(farmId), setOf(cropId))
        val params = completedScopeParams(memberId, setOf(farmId), setOf(cropId))
        where += cursorPredicate()
        params["cursorEndsAt"] = endsAt
        params["cursorFinalHarvestRecordId"] = finalHarvestRecordId

        return findCompleted(where, params, maxResults = 1).firstOrNull()
    }

    private fun findCompleted(
        where: List<String>,
        params: Map<String, Any>,
        maxResults: Int,
    ): List<FarmingCycleReport> {
        val query = entityManager.createQuery(
            """
            select r
            from FarmingCycleReport r
            join fetch r.farm
            join fetch r.crop
            where ${where.joinToString(" and ")}
            order by r.endsAt desc, r.finalHarvestRecord.id desc
            """.trimIndent(),
            FarmingCycleReport::class.java,
        )
        params.forEach(query::setParameter)
        query.maxResults = maxResults
        return query.resultList
    }

    private fun completedScopeWhere(
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): MutableList<String> = mutableListOf(
        "r.member.id = :memberId",
        "r.status = :completed",
    ).apply {
        farmIds.takeIf { it.isNotEmpty() }?.let { add("r.farm.id in :farmIds") }
        cropIds.takeIf { it.isNotEmpty() }?.let { add("r.crop.id in :cropIds") }
    }

    private fun completedScopeParams(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): MutableMap<String, Any> = mutableMapOf<String, Any>(
        "memberId" to memberId,
        "completed" to FarmingCycleReportStatus.COMPLETED,
    ).apply {
        farmIds.takeIf { it.isNotEmpty() }?.let { put("farmIds", it) }
        cropIds.takeIf { it.isNotEmpty() }?.let { put("cropIds", it) }
    }

    private fun workItemScopeWhere(
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): MutableList<String> = mutableListOf(
        "r.member.id = :memberId",
        "r.status in :visibleStatuses",
    ).apply {
        farmIds.takeIf { it.isNotEmpty() }?.let { add("r.farm.id in :farmIds") }
        cropIds.takeIf { it.isNotEmpty() }?.let { add("r.crop.id in :cropIds") }
    }

    private fun workItemScopeParams(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): MutableMap<String, Any> = mutableMapOf<String, Any>(
        "memberId" to memberId,
        "visibleStatuses" to setOf(
            FarmingCycleReportStatus.ACTIVE,
            FarmingCycleReportStatus.COMPLETED,
        ),
    ).apply {
        farmIds.takeIf { it.isNotEmpty() }?.let { put("farmIds", it) }
        cropIds.takeIf { it.isNotEmpty() }?.let { put("cropIds", it) }
    }

    private fun cursorPredicate(): String =
        """
        (
          r.endsAt < :cursorEndsAt
          or (
            r.endsAt = :cursorEndsAt
            and r.finalHarvestRecord.id < :cursorFinalHarvestRecordId
          )
        )
        """.trimIndent()

    private fun toWorkProjection(row: Array<Any>): WorkProjection =
        WorkProjection(
            reportId = row[0] as UUID,
            status = row[1] as FarmingCycleReportStatus,
            farmId = row[2] as UUID,
            farmName = row[3] as String,
            cropId = row[4] as UUID,
            cropName = row[5] as String,
            startsAt = row[6] as LocalDateTime,
            endsAt = row[7] as LocalDateTime?,
            finalHarvestRecordId = row[8] as UUID?,
            statistics = row[9] as CycleReportStatistics,
        )

    private data class WorkProjection(
        val reportId: UUID,
        val status: FarmingCycleReportStatus,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val finalHarvestRecordId: UUID?,
        val statistics: CycleReportStatistics,
    ) {
        fun toWorkItem(workType: WorkType): FarmingCycleReportQueryRepository.WorkItem? {
            val recordCount = statistics.recordCountFor(workType)
            if (recordCount <= 0) return null
            return FarmingCycleReportQueryRepository.WorkItem(
                reportId = reportId,
                status = status,
                farmId = farmId,
                farmName = farmName,
                cropId = cropId,
                cropName = cropName,
                startsAt = startsAt,
                endsAt = endsAt,
                finalHarvestRecordId = finalHarvestRecordId,
                workType = workType,
                recordCount = recordCount,
                lastWorkedOn = statistics.lastWorkedOnFor(workType),
            )
        }
    }

    private fun FarmingCycleReportQueryRepository.WorkItem.isAfter(
        cursor: FarmingCycleReportQueryRepository.WorkItemCursor,
    ): Boolean = workItemSortKeyComparator.compare(sortKey(), cursor.sortKey()) > 0

    private fun FarmingCycleReportQueryRepository.WorkItem.sortKey() =
        WorkItemSortKey(
            lastWorkedOn = lastWorkedOn,
            statusRank = status.rank(),
            sortAt = sortAt,
            reportId = reportId,
            workTypeOrdinal = workType.ordinal,
        )

    private fun FarmingCycleReportQueryRepository.WorkItemCursor.sortKey() =
        WorkItemSortKey(
            lastWorkedOn = lastWorkedOn,
            statusRank = status.rank(),
            sortAt = sortAt,
            reportId = reportId,
            workTypeOrdinal = workType.ordinal,
        )

    private fun FarmingCycleReportStatus.rank(): Int = when (this) {
        FarmingCycleReportStatus.ACTIVE -> 0
        FarmingCycleReportStatus.COMPLETED -> 1
        FarmingCycleReportStatus.SUPERSEDED -> 2
    }

    private data class WorkItemSortKey(
        val lastWorkedOn: LocalDate?,
        val statusRank: Int,
        val sortAt: LocalDateTime,
        val reportId: UUID,
        val workTypeOrdinal: Int,
    )

    private val workItemSortKeyComparator =
        compareByDescending<WorkItemSortKey> { it.lastWorkedOn ?: LocalDate.MIN }
            .thenBy { it.statusRank }
            .thenByDescending { it.sortAt }
            .thenByDescending { it.reportId }
            .thenBy { it.workTypeOrdinal }

    private val workItemComparator =
        Comparator<FarmingCycleReportQueryRepository.WorkItem> { left, right ->
            workItemSortKeyComparator.compare(left.sortKey(), right.sortKey())
        }
}
