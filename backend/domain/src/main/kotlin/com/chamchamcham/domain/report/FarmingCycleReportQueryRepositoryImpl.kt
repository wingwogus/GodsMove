package com.chamchamcham.domain.report

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class FarmingCycleReportQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : FarmingCycleReportQueryRepository {
    override fun searchCompleted(
        condition: FarmingCycleReportQueryRepository.SearchCondition,
    ): FarmingCycleReportQueryRepository.SearchResult {
        val where = completedScopeWhere(condition.farmId, condition.cropId)
        val params = completedScopeParams(condition.memberId, condition.farmId, condition.cropId)

        condition.cursor?.let { cursor ->
            where += cursorPredicate()
            params["cursorEndsAt"] = cursor.endsAt
            params["cursorFinalHarvestRecordId"] = cursor.finalHarvestRecordId
        }

        return FarmingCycleReportQueryRepository.SearchResult(
            rows = findCompleted(where, params, condition.size),
        )
    }

    override fun findPreviousCompleted(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
        endsAt: LocalDateTime,
        finalHarvestRecordId: UUID,
    ): FarmingCycleReport? {
        val where = completedScopeWhere(farmId, cropId)
        val params = completedScopeParams(memberId, farmId, cropId)
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
        farmId: UUID?,
        cropId: UUID?,
    ): MutableList<String> = mutableListOf(
        "r.member.id = :memberId",
        "r.status = :completed",
    ).apply {
        farmId?.let { add("r.farm.id = :farmId") }
        cropId?.let { add("r.crop.id = :cropId") }
    }

    private fun completedScopeParams(
        memberId: UUID,
        farmId: UUID?,
        cropId: UUID?,
    ): MutableMap<String, Any> = mutableMapOf<String, Any>(
        "memberId" to memberId,
        "completed" to FarmingCycleReportStatus.COMPLETED,
    ).apply {
        farmId?.let { put("farmId", it) }
        cropId?.let { put("cropId", it) }
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
}
