package com.chamchamcham.domain.farming

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FarmingCycleReportSourceRepositoryImpl(
    private val entityManager: EntityManager,
) : FarmingCycleReportSourceRepository {
    override fun load(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportSourceSnapshot {
        val records = entityManager.createQuery(
            """
            select record
              from FarmingRecord record
             where record.member.id = :memberId
               and record.farm.id = :farmId
               and record.crop.id = :cropId
               and record.isDeleted = false
             order by record.workedAt asc, record.id asc
            """.trimIndent(),
            FarmingRecord::class.java,
        )
            .setParameter("memberId", memberId)
            .setParameter("farmId", farmId)
            .setParameter("cropId", cropId)
            .resultList
        if (records.isEmpty()) {
            return FarmingCycleReportSourceSnapshot(
                records = emptyList(),
                plantingByRecordId = emptyMap(),
                wateringByRecordId = emptyMap(),
                fertilizingByRecordId = emptyMap(),
                pestControlByRecordId = emptyMap(),
                weedingByRecordId = emptyMap(),
                harvestByRecordId = emptyMap(),
                mediaRecordIds = emptySet(),
            )
        }

        val recordIds = records.map { requireNotNull(it.id) { "Persisted farming record id is required" } }
        return FarmingCycleReportSourceSnapshot(
            records = records,
            plantingByRecordId = loadDetails(PlantingRecord::class.java, recordIds).byRecordId { it.record },
            wateringByRecordId = loadDetails(WateringRecord::class.java, recordIds).byRecordId { it.record },
            fertilizingByRecordId = loadDetails(FertilizingRecord::class.java, recordIds).byRecordId { it.record },
            pestControlByRecordId = loadDetails(PestControlRecord::class.java, recordIds).byRecordId { it.record },
            weedingByRecordId = loadDetails(WeedingRecord::class.java, recordIds).byRecordId { it.record },
            harvestByRecordId = loadDetails(HarvestRecord::class.java, recordIds).byRecordId { it.record },
            mediaRecordIds = entityManager.createQuery(
                """
                select distinct media.record.id
                  from FarmingRecordMedia media
                 where media.record.id in :recordIds
                """.trimIndent(),
                UUID::class.java,
            )
                .setParameter("recordIds", recordIds)
                .resultList
                .toSet(),
        )
    }

    private fun <T : Any> loadDetails(type: Class<T>, recordIds: Collection<UUID>): List<T> =
        entityManager.createQuery(
            "select detail from ${type.simpleName} detail where detail.record.id in :recordIds",
            type,
        )
            .setParameter("recordIds", recordIds)
            .resultList

    private fun <T> List<T>.byRecordId(recordOf: (T) -> FarmingRecord): Map<UUID, T> =
        associateBy { detail ->
            val record = recordOf(detail)
            requireNotNull(record.id) { "Persisted farming record id is required" }
        }
}
