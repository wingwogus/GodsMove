package com.chamchamcham.application.report

import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import java.time.LocalDateTime
import java.util.UUID

object FarmingCycleReportResult {
    data class Metadata(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val sourceRevision: Long,
    )

    data class Snapshot(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val status: FarmingCycleReportStatus,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val startBasis: FarmingCycleStartBasis,
        val finalHarvestRecordId: UUID?,
        val statisticsSchemaVersion: Int,
        val sourceRevision: Long,
        val statistics: CycleReportStatistics,
    )

    data class Current(
        val current: Snapshot?,
        val previous: Snapshot?,
    )

    data class Detail(
        val selected: Snapshot,
        val previous: Snapshot?,
    )

    data class Page(
        val items: List<Metadata>,
        val nextCursor: String?,
    )
}
