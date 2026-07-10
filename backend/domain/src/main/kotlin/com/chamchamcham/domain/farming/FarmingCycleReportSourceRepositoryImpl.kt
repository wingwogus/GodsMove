package com.chamchamcham.domain.farming

import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FarmingCycleReportSourceRepositoryImpl(
    private val farmingRecordRepository: FarmingRecordRepository,
    private val plantingRecordRepository: PlantingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val fertilizingRecordRepository: FertilizingRecordRepository,
    private val pestControlRecordRepository: PestControlRecordRepository,
    private val weedingRecordRepository: WeedingRecordRepository,
    private val harvestRecordRepository: HarvestRecordRepository,
    private val farmingRecordMediaRepository: FarmingRecordMediaRepository,
) : FarmingCycleReportSourceRepository {
    override fun load(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportSourceSnapshot {
        val records = farmingRecordRepository.findReportSourceRecords(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
        )
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
            plantingByRecordId = plantingRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            wateringByRecordId = wateringRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            fertilizingByRecordId = fertilizingRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            pestControlByRecordId = pestControlRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            weedingByRecordId = weedingRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            harvestByRecordId = harvestRecordRepository.findByRecord_IdIn(recordIds).byRecordId { it.record },
            mediaRecordIds = farmingRecordMediaRepository.findDistinctRecordIdsByRecordIdIn(recordIds),
        )
    }

    private fun <T> List<T>.byRecordId(recordOf: (T) -> FarmingRecord): Map<UUID, T> =
        associateBy { detail ->
            val record = recordOf(detail)
            requireNotNull(record.id) { "Persisted farming record id is required" }
        }
}
