package com.chamchamcham.domain.farming

import java.util.UUID

data class FarmingCycleReportSourceSnapshot(
    val records: List<FarmingRecord>,
    val plantingByRecordId: Map<UUID, PlantingRecord>,
    val wateringByRecordId: Map<UUID, WateringRecord>,
    val fertilizingByRecordId: Map<UUID, FertilizingRecord>,
    val pestControlByRecordId: Map<UUID, PestControlRecord>,
    val weedingByRecordId: Map<UUID, WeedingRecord>,
    val harvestByRecordId: Map<UUID, HarvestRecord>,
    val mediaRecordIds: Set<UUID>,
)

interface FarmingCycleReportSourceRepository {
    fun load(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportSourceSnapshot
}
