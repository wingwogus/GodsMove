package com.chamchamcham.domain.farming

import java.util.UUID

data class FarmingWorkReportSourceSnapshot(
    val records: List<FarmingRecord>,
    val finalHarvestRecordIds: Set<UUID>,
    val firstImageUrlByRecordId: Map<UUID, String>,
)

interface FarmingWorkReportSourceRepository {
    fun load(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): FarmingWorkReportSourceSnapshot
}
