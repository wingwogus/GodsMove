package com.chamchamcham.domain.farming

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FarmingWorkReportSourceRepositoryImpl(
    private val entityManager: EntityManager,
) : FarmingWorkReportSourceRepository {
    override fun load(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): FarmingWorkReportSourceSnapshot {
        val records = findRecords(memberId, farmIds, cropIds)
        val recordIds = records.mapTo(mutableSetOf()) {
            requireNotNull(it.id) { "Persisted farming record id is required" }
        }
        val queryRecordIds = recordIds.ifEmpty { setOf(EMPTY_RECORD_ID) }

        return FarmingWorkReportSourceSnapshot(
            records = records,
            finalHarvestRecordIds = findFinalHarvestRecordIds(queryRecordIds),
            firstImageUrlByRecordId = findFirstImageUrls(queryRecordIds),
        )
    }

    private fun findRecords(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): List<FarmingRecord> =
        entityManager.createQuery(
            """
            select distinct r
            from FarmingRecord r
            join fetch r.member
            join fetch r.farm f
            left join fetch f.boundaryCoordinates
            join fetch r.crop
            where r.member.id = :memberId
              and r.farm.id in :farmIds
              and r.crop.id in :cropIds
              and r.isDeleted = false
            """.trimIndent(),
            FarmingRecord::class.java,
        )
            .setParameter("memberId", memberId)
            .setParameter("farmIds", farmIds.ifEmpty { setOf(EMPTY_RECORD_ID) })
            .setParameter("cropIds", cropIds.ifEmpty { setOf(EMPTY_RECORD_ID) })
            .resultList

    private fun findFinalHarvestRecordIds(recordIds: Set<UUID>): Set<UUID> =
        entityManager.createQuery(
            """
            select h.record.id
            from HarvestRecord h
            where h.record.id in :recordIds
              and h.isLastHarvest = true
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("recordIds", recordIds)
            .resultList
            .toSet()

    private fun findFirstImageUrls(recordIds: Set<UUID>): Map<UUID, String> =
        entityManager.createQuery(
            """
            select m.record.id, m.id, m.displayOrder, m.uploadedMedia.fileUrl
            from FarmingRecordMedia m
            where m.record.id in :recordIds
            """.trimIndent(),
            Array<Any>::class.java,
        )
            .setParameter("recordIds", recordIds)
            .resultList
            .map { row ->
                MediaProjection(
                    recordId = row[0] as UUID,
                    mediaId = row[1] as UUID,
                    displayOrder = row[2] as Int,
                    fileUrl = row[3] as String,
                )
            }
            .groupBy(MediaProjection::recordId)
            .mapValues { (_, media) ->
                media.minWith(compareBy(MediaProjection::displayOrder, MediaProjection::mediaId)).fileUrl
            }

    private data class MediaProjection(
        val recordId: UUID,
        val mediaId: UUID,
        val displayOrder: Int,
        val fileUrl: String,
    )

    companion object {
        private val EMPTY_RECORD_ID: UUID = UUID(0, 0)
    }
}
