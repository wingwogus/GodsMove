package com.chamchamcham.domain.farming

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FarmingRecordQueryRepositoryImpl(
    private val entityManager: EntityManager
) : FarmingRecordQueryRepository {
    override fun search(condition: FarmingRecordQueryRepository.SearchCondition): FarmingRecordQueryRepository.SearchResult {
        val records = findRecords(condition)
        if (records.isEmpty()) {
            return FarmingRecordQueryRepository.SearchResult(emptyList())
        }

        val recordIds = records.map { requireNotNull(it.id) { "Persisted farming record id is required" } }
        val thumbnails = findThumbnails(recordIds)

        return FarmingRecordQueryRepository.SearchResult(
            rows = records.map { record ->
                FarmingRecordQueryRepository.Row(
                    record = record,
                    thumbnailUrl = thumbnails[record.id]
                )
            }
        )
    }

    private fun findRecords(condition: FarmingRecordQueryRepository.SearchCondition): List<FarmingRecord> {
        val where = mutableListOf("r.isDeleted = false", "r.member.id = :memberId")
        val params = mutableMapOf<String, Any>("memberId" to condition.memberId)

        condition.cropId?.let {
            where += "r.crop.id = :cropId"
            params["cropId"] = it
        }
        condition.workType?.let {
            where += "r.workType = :workType"
            params["workType"] = it
        }
        condition.workedAtFrom?.let {
            where += "r.workedAt >= :workedAtFrom"
            params["workedAtFrom"] = it
        }
        condition.workedAtTo?.let {
            where += "r.workedAt < :workedAtTo"
            params["workedAtTo"] = it
        }

        val keywordPredicates = mutableListOf<String>()
        condition.keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { kw ->
            params["keyword"] = "%$kw%"
            keywordPredicates += "lower(r.crop.name) like :keyword"
            keywordPredicates += "lower(r.memo) like :keyword"
            keywordPredicates += "exists (select 1 from FertilizingRecord f where f.record.id = r.id and lower(f.materialName) like :keyword)"
            keywordPredicates += "exists (select 1 from PestControlRecord p where p.record.id = r.id and lower(p.pesticideName) like :keyword)"
        }
        if (condition.matchedWorkTypes.isNotEmpty()) {
            keywordPredicates += "r.workType in :matchedWorkTypes"
            params["matchedWorkTypes"] = condition.matchedWorkTypes
        }
        if (condition.matchedParts.isNotEmpty()) {
            keywordPredicates += "exists (select 1 from HarvestRecord h where h.record.id = r.id and h.medicinalPart in :matchedParts)"
            params["matchedParts"] = condition.matchedParts
        }
        if (keywordPredicates.isNotEmpty()) {
            where += "(${keywordPredicates.joinToString(" or ")})"
        }

        condition.cursor?.let { cursor ->
            where += "(r.workedAt < :cursorWorkedAt or (r.workedAt = :cursorWorkedAt and r.id < :cursorId))"
            params["cursorWorkedAt"] = cursor.workedAt
            params["cursorId"] = cursor.id
        }

        val query = entityManager.createQuery(
            """
            select r
            from FarmingRecord r
            where ${where.joinToString(" and ")}
            order by r.workedAt desc, r.id desc
            """.trimIndent(),
            FarmingRecord::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        return query.resultList
    }

    private fun findThumbnails(recordIds: List<UUID>): Map<UUID, String> {
        val media = entityManager.createQuery(
            """
            select m
            from FarmingRecordMedia m
            where m.record.id in :recordIds
            order by m.displayOrder asc
            """.trimIndent(),
            FarmingRecordMedia::class.java
        )
            .setParameter("recordIds", recordIds)
            .resultList

        return media
            .groupBy { requireNotNull(it.record.id) { "Persisted farming record id is required" } }
            .mapValues { (_, rows) -> rows.first().uploadedMedia.fileUrl }
    }
}
