package com.chamchamcham.domain.farming

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FarmingRecordQueryRepositoryImpl(
    private val entityManager: EntityManager
) : FarmingRecordQueryRepository {
    override fun search(condition: FarmingRecordQueryRepository.SearchCondition): FarmingRecordQueryRepository.SearchResult {
        val selectedRecords = findRecords(condition)
        if (selectedRecords.isEmpty()) {
            return FarmingRecordQueryRepository.SearchResult(emptyList())
        }

        val recordIds = selectedRecords.map { requireNotNull(it[0] as FarmingRecord).let { r -> requireNotNull(r.id) { "Persisted farming record id is required" } } }
        val thumbnails = findThumbnails(recordIds)

        return FarmingRecordQueryRepository.SearchResult(
            rows = selectedRecords.map { row ->
                val record = row[0] as FarmingRecord
                FarmingRecordQueryRepository.Row(
                    record = record,
                    thumbnailUrl = thumbnails[record.id],
                    irrigationMethod = row[1] as IrrigationMethod?,
                    harvestAmount = row[2] as java.math.BigDecimal?,
                    pesticideName = row[3] as String?,
                    weedingMethod = row[4] as WeedingMethod?,
                    plantingMethod = row[5] as PlantingMethod?,
                    materialName = row[6] as String?,
                )
            }
        )
    }

    override fun count(condition: FarmingRecordQueryRepository.SearchCondition): Long {
        val (where, params) = buildFilterPredicates(condition)

        val query = entityManager.createQuery(
            """
            select count(r)
            from FarmingRecord r
            where ${where.joinToString(" and ")}
            """.trimIndent(),
            Long::class.javaObjectType
        )
        params.forEach(query::setParameter)
        return query.singleResult
    }

    private fun buildFilterPredicates(
        condition: FarmingRecordQueryRepository.SearchCondition
    ): Pair<MutableList<String>, MutableMap<String, Any>> {
        val where = mutableListOf("r.isDeleted = false", "r.member.id = :memberId")
        val params = mutableMapOf<String, Any>("memberId" to condition.memberId)

        if (condition.cropIds.isNotEmpty()) {
            where += "r.crop.id in :cropIds"
            params["cropIds"] = condition.cropIds
        }
        if (condition.workTypes.isNotEmpty()) {
            where += "r.workType in :workTypes"
            params["workTypes"] = condition.workTypes
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
            keywordPredicates += "exists (select 1 from PestControlRecord p where p.record.id = r.id and (lower(p.pesticide.brandName) like :keyword or lower(p.pesticide.itemName) like :keyword))"
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

        return where to params
    }

    private fun findRecords(condition: FarmingRecordQueryRepository.SearchCondition): List<Array<Any?>> {
        val (where, params) = buildFilterPredicates(condition)

        condition.cursor?.let { cursor ->
            where += "(r.workedAt < :cursorWorkedAt or (r.workedAt = :cursorWorkedAt and r.id < :cursorId))"
            params["cursorWorkedAt"] = cursor.workedAt
            params["cursorId"] = cursor.id
        }

        val query = entityManager.createQuery(
            """
            select r, w.irrigationMethod, h.harvestAmount, pest.itemName, wd.weedingMethod, pl.plantingMethod, fz.materialName
            from FarmingRecord r
            left join WateringRecord w on w.record = r
            left join HarvestRecord h on h.record = r
            left join PestControlRecord p on p.record = r
            left join p.pesticide pest
            left join WeedingRecord wd on wd.record = r
            left join PlantingRecord pl on pl.record = r
            left join FertilizingRecord fz on fz.record = r
            where ${where.joinToString(" and ")}
            order by r.workedAt desc, r.id desc
            """.trimIndent(),
            Array<Any>::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<Array<Any?>>
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
