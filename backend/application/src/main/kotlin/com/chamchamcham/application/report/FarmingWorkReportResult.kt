package com.chamchamcham.application.report

import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.WateringStatistics
import com.chamchamcham.domain.report.WeedingStatistics
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object FarmingWorkReportResult {
    data class Item(
        val reportId: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val finalHarvestRecordId: UUID,
        val workType: WorkType,
        val workTypeLabel: String,
        val recordCount: Int,
        val lastWorkedOn: LocalDate?,
        val thumbnailUrl: String?,
    )

    data class Page(
        val items: List<Item>,
        val nextCursor: String?,
    )

    data class Detail(
        val reportId: UUID,
        val workType: WorkType,
        val workTypeLabel: String,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val statistics: WorkStatistics,
        val feedback: FeedbackStatus,
    )

    data class WorkStatistics(
        val common: CommonOnlyStatistics,
        val planting: PlantingStatistics? = null,
        val watering: WateringStatistics? = null,
        val fertilizing: FertilizingStatistics? = null,
        val pestControl: PestControlStatistics? = null,
        val weeding: WeedingStatistics? = null,
        val harvest: HarvestStatistics? = null,
    )

    data class FeedbackStatus(
        val status: ReportFeedbackStatus,
        val content: ReportFeedbackResultContent?,
    )
}
