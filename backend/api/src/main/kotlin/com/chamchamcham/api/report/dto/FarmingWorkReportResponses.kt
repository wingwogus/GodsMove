package com.chamchamcham.api.report.dto

import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.CommonStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.FertilizingStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.HarvestStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.PestControlStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.PlantingStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.WateringStatisticsResponse
import com.chamchamcham.api.report.dto.FarmingReportStatisticsResponses.WeedingStatisticsResponse
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.application.report.FarmingWorkReportResult
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object FarmingWorkReportResponses {
    data class ItemResponse(
        val reportId: UUID,
        val status: FarmingCycleReportStatus,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val workType: WorkType,
        val workTypeLabel: String,
        val recordCount: Int,
        val lastWorkedOn: LocalDate?,
        val thumbnailUrl: String?,
    ) {
        companion object {
            fun from(source: FarmingWorkReportResult.Item) = ItemResponse(
                reportId = source.reportId,
                status = source.status,
                farmId = source.farmId,
                farmName = source.farmName,
                cropId = source.cropId,
                cropName = source.cropName,
                startsAt = source.startsAt,
                endsAt = source.endsAt,
                workType = source.workType,
                workTypeLabel = source.workTypeLabel,
                recordCount = source.recordCount,
                lastWorkedOn = source.lastWorkedOn,
                thumbnailUrl = source.thumbnailUrl,
            )
        }
    }

    data class PageResponse(
        val items: List<ItemResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(source: FarmingWorkReportResult.Page) = PageResponse(
                items = source.items.map(ItemResponse::from),
                nextCursor = source.nextCursor,
            )
        }
    }

    data class DetailResponse(
        val reportId: UUID,
        val status: FarmingCycleReportStatus,
        val workType: WorkType,
        val workTypeLabel: String,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime?,
        val statistics: StatisticsResponse,
        val feedback: FeedbackStatusResponse?,
    ) {
        companion object {
            fun from(source: FarmingWorkReportResult.Detail) = DetailResponse(
                reportId = source.reportId,
                status = source.status,
                workType = source.workType,
                workTypeLabel = source.workTypeLabel,
                farmId = source.farmId,
                farmName = source.farmName,
                cropId = source.cropId,
                cropName = source.cropName,
                startsAt = source.startsAt,
                endsAt = source.endsAt,
                statistics = StatisticsResponse.from(source.statistics),
                feedback = source.feedback?.let(FeedbackStatusResponse::from),
            )
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class StatisticsResponse(
        val common: CommonStatisticsResponse,
        val planting: PlantingStatisticsResponse?,
        val watering: WateringStatisticsResponse?,
        val fertilizing: FertilizingStatisticsResponse?,
        val pestControl: PestControlStatisticsResponse?,
        val weeding: WeedingStatisticsResponse?,
        val harvest: HarvestStatisticsResponse?,
    ) {
        companion object {
            fun from(source: FarmingWorkReportResult.WorkStatistics) = StatisticsResponse(
                common = CommonStatisticsResponse.from(source.common),
                planting = source.planting?.let(PlantingStatisticsResponse::from),
                watering = source.watering?.let(WateringStatisticsResponse::from),
                fertilizing = source.fertilizing?.let(FertilizingStatisticsResponse::from),
                pestControl = source.pestControl?.let(PestControlStatisticsResponse::from),
                weeding = source.weeding?.let(WeedingStatisticsResponse::from),
                harvest = source.harvest?.let(HarvestStatisticsResponse::from),
            )
        }
    }

    data class FeedbackStatusResponse(
        val status: ReportFeedbackStatus,
        val content: FeedbackContentResponse?,
    ) {
        companion object {
            fun from(source: FarmingWorkReportResult.FeedbackStatus) = FeedbackStatusResponse(
                status = source.status,
                content = source.content?.let(FeedbackContentResponse::from),
            )
        }
    }

    data class FeedbackContentResponse(
        val summary: String,
        val comparisons: List<FeedbackItemResponse>,
        val strengths: List<FeedbackItemResponse>,
        val improvements: List<FeedbackItemResponse>,
        val nextActions: List<FeedbackItemResponse>,
    ) {
        companion object {
            fun from(source: ReportFeedbackResultContent) = FeedbackContentResponse(
                summary = source.summary,
                comparisons = source.comparisons.map(FeedbackItemResponse::from),
                strengths = source.strengths.map(FeedbackItemResponse::from),
                improvements = source.improvements.map(FeedbackItemResponse::from),
                nextActions = source.nextActions.map(FeedbackItemResponse::from),
            )
        }
    }

    data class FeedbackItemResponse(
        val text: String,
    ) {
        companion object {
            fun from(source: ReportFeedbackItemResult) = FeedbackItemResponse(text = source.text)
        }
    }
}
