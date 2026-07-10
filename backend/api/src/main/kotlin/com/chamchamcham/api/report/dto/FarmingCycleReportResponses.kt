package com.chamchamcham.api.report.dto

import com.chamchamcham.application.report.FarmingCycleReportResult
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import java.time.LocalDateTime
import java.util.UUID

object FarmingCycleReportResponses {
    data class MetadataResponse(
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
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Metadata) =
                MetadataResponse(
                    id = source.id,
                    farmId = source.farmId,
                    farmName = source.farmName,
                    cropId = source.cropId,
                    cropName = source.cropName,
                    status = source.status,
                    startsAt = source.startsAt,
                    endsAt = source.endsAt,
                    startBasis = source.startBasis,
                    sourceRevision = source.sourceRevision,
                )
        }
    }

    data class SnapshotResponse(
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
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Snapshot) =
                SnapshotResponse(
                    id = source.id,
                    farmId = source.farmId,
                    farmName = source.farmName,
                    cropId = source.cropId,
                    cropName = source.cropName,
                    status = source.status,
                    startsAt = source.startsAt,
                    endsAt = source.endsAt,
                    startBasis = source.startBasis,
                    finalHarvestRecordId = source.finalHarvestRecordId,
                    statisticsSchemaVersion = source.statisticsSchemaVersion,
                    sourceRevision = source.sourceRevision,
                    statistics = source.statistics,
                )
        }
    }

    data class CurrentResponse(
        val current: SnapshotResponse?,
        val previous: SnapshotResponse?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Current) =
                CurrentResponse(
                    current = source.current?.let(SnapshotResponse::from),
                    previous = source.previous?.let(SnapshotResponse::from),
                )
        }
    }

    data class DetailResponse(
        val selected: SnapshotResponse,
        val previous: SnapshotResponse?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Detail) =
                DetailResponse(
                    selected = SnapshotResponse.from(source.selected),
                    previous = source.previous?.let(SnapshotResponse::from),
                )
        }
    }

    data class PageResponse(
        val items: List<MetadataResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(source: FarmingCycleReportResult.Page) =
                PageResponse(
                    items = source.items.map(MetadataResponse::from),
                    nextCursor = source.nextCursor,
                )
        }
    }
}
