package com.chamchamcham.application.report

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.coaching.common.toCoachingText
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingWorkReportSourceRepository
import com.chamchamcham.domain.farming.FarmingWorkReportSourceSnapshot
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.PestControlStatistics
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.WateringStatistics
import com.chamchamcham.domain.report.WeedingStatistics
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class FarmingWorkReportQueryService(
    private val queryRepository: FarmingCycleReportQueryRepository,
    private val sourceRepository: FarmingWorkReportSourceRepository,
    private val partitioner: FarmingCyclePartitioner,
    private val cursorCodec: OpaqueCursorCodec,
    private val reportRepository: FarmingCycleReportRepository,
    private val feedbackQueryService: ReportFeedbackQueryService,
) {
    fun list(condition: FarmingWorkReportSearchCondition): FarmingWorkReportResult.Page {
        val result = queryRepository.searchCompletedWorkItems(
            FarmingCycleReportQueryRepository.WorkItemSearchCondition(
                memberId = condition.memberId,
                farmId = condition.farmId,
                cropId = condition.cropId,
                workType = condition.workType,
                cursor = decodeCursor(condition.cursor),
                size = condition.size + 1,
            ),
        )
        val visibleRows = result.rows.take(condition.size)
        val snapshot = sourceRepository.load(
            memberId = condition.memberId,
            farmIds = visibleRows.mapTo(mutableSetOf(), FarmingCycleReportQueryRepository.WorkItem::farmId),
            cropIds = visibleRows.mapTo(mutableSetOf(), FarmingCycleReportQueryRepository.WorkItem::cropId),
        )
        val slicesByScope = buildSlicesByScope(visibleRows, snapshot)

        return FarmingWorkReportResult.Page(
            items = visibleRows.map { row ->
                row.toResult(
                    thumbnailUrl = findThumbnailUrl(row, slicesByScope[row.scopeKey()].orEmpty(), snapshot),
                )
            },
            nextCursor = if (result.rows.size > condition.size) {
                visibleRows.lastOrNull()?.let(::encodeCursor)
            } else {
                null
            },
        )
    }

    fun getDetail(
        memberId: UUID,
        reportId: UUID,
        workType: WorkType,
    ): FarmingWorkReportResult.Detail {
        val report = reportRepository.findByIdAndMember_Id(reportId, memberId)
            ?.takeIf { it.status == FarmingCycleReportStatus.COMPLETED }
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)
        val statistics = report.statistics.forWorkType(workType)
        if (statistics.common.recordCount == 0) {
            throw BusinessException(ErrorCode.WORK_REPORT_NOT_FOUND)
        }
        val feedback = feedbackQueryService.get(memberId, reportId).feedbacks
            .firstOrNull { it.workType == workType }

        return FarmingWorkReportResult.Detail(
            reportId = requireNotNull(report.id) { "Persisted farming cycle report id is required" },
            workType = workType,
            workTypeLabel = workType.toCoachingText(),
            farmId = requireNotNull(report.farm.id) { "Persisted farm id is required" },
            farmName = report.farm.name,
            cropId = requireNotNull(report.crop.id) { "Persisted crop id is required" },
            cropName = report.crop.name,
            startsAt = report.startsAt,
            endsAt = requireNotNull(report.endsAt) { "Completed report endsAt is required" },
            statistics = statistics,
            feedback = FarmingWorkReportResult.FeedbackStatus(
                status = feedback?.status ?: ReportFeedbackStatus.PENDING,
                content = feedback?.content,
            ),
        )
    }

    private fun buildSlicesByScope(
        rows: List<FarmingCycleReportQueryRepository.WorkItem>,
        snapshot: FarmingWorkReportSourceSnapshot,
    ): Map<WorkScope, List<CycleSlice>> =
        rows.map { row -> row.scopeKey() }
            .distinct()
            .associateWith { scope ->
                partitioner.partition(
                    snapshot.records
                        .asSequence()
                        .filter { record -> record.matches(scope) }
                        .map { record -> record.toCycleSource(snapshot) }
                        .toList(),
                )
            }

    private fun findThumbnailUrl(
        row: FarmingCycleReportQueryRepository.WorkItem,
        slices: List<CycleSlice>,
        snapshot: FarmingWorkReportSourceSnapshot,
    ): String? {
        val cycle = slices.singleOrNull { slice ->
            slice.finalHarvestRecordId == row.finalHarvestRecordId
        } ?: return null
        val picturedRecord = cycle.records
            .asSequence()
            .filter { record -> record.workType == row.workType }
            .filter { record -> snapshot.firstImageUrlByRecordId.containsKey(record.id) }
            .maxWithOrNull(
                compareBy(
                    CycleReportSourceRecord::workedAt,
                    CycleReportSourceRecord::createdAt,
                    CycleReportSourceRecord::id,
                ),
            )
            ?: return null
        return snapshot.firstImageUrlByRecordId[picturedRecord.id]
    }

    private fun decodeCursor(cursor: String?): FarmingCycleReportQueryRepository.WorkItemCursor? {
        if (cursor.isNullOrBlank()) return null
        val payload = cursorCodec.decode(cursor, FarmingWorkReportCursorPayload::class.java)
        return FarmingCycleReportQueryRepository.WorkItemCursor(
            endsAt = payload.endsAt,
            reportId = payload.reportId,
            workType = payload.workType,
        )
    }

    private fun encodeCursor(row: FarmingCycleReportQueryRepository.WorkItem): String =
        cursorCodec.encode(
            FarmingWorkReportCursorPayload(
                endsAt = row.endsAt,
                reportId = row.reportId,
                workType = row.workType,
            ),
        )

    private fun FarmingCycleReportQueryRepository.WorkItem.toResult(
        thumbnailUrl: String?,
    ): FarmingWorkReportResult.Item =
        FarmingWorkReportResult.Item(
            reportId = reportId,
            farmId = farmId,
            farmName = farmName,
            cropId = cropId,
            cropName = cropName,
            startsAt = startsAt,
            endsAt = endsAt,
            finalHarvestRecordId = finalHarvestRecordId,
            workType = workType,
            workTypeLabel = workType.toCoachingText(),
            recordCount = recordCount,
            lastWorkedOn = lastWorkedOn,
            thumbnailUrl = thumbnailUrl,
        )

    private fun FarmingRecord.toCycleSource(
        snapshot: FarmingWorkReportSourceSnapshot,
    ): CycleReportSourceRecord {
        val recordId = requireNotNull(id) { "Persisted farming record id is required" }
        return CycleReportSourceRecord(
            id = recordId,
            workedAt = workedAt,
            createdAt = createdAt,
            workType = workType,
            weatherCondition = weatherCondition,
            weatherTemperature = weatherTemperature,
            hasPhoto = snapshot.firstImageUrlByRecordId.containsKey(recordId),
            memo = memo,
            harvest = if (workType == WorkType.HARVEST) {
                HarvestReportSource(
                    amountKg = null,
                    medicinalPart = null,
                    growthPeriodMonths = null,
                    isLastHarvest = recordId in snapshot.finalHarvestRecordIds,
                )
            } else {
                null
            },
        )
    }

    private fun FarmingCycleReportQueryRepository.WorkItem.scopeKey(): WorkScope =
        WorkScope(farmId, cropId)

    private fun FarmingRecord.matches(scope: WorkScope): Boolean =
        farm.id == scope.farmId && crop.id == scope.cropId

    private data class WorkScope(
        val farmId: UUID,
        val cropId: UUID,
    )
}

private fun CycleReportStatistics.forWorkType(workType: WorkType): FarmingWorkReportResult.WorkStatistics =
    when (workType) {
        WorkType.PLANTING -> FarmingWorkReportResult.WorkStatistics(
            common = planting.toCommon(),
            planting = planting,
        )
        WorkType.WATERING -> FarmingWorkReportResult.WorkStatistics(
            common = watering.toCommon(),
            watering = watering,
        )
        WorkType.FERTILIZING -> FarmingWorkReportResult.WorkStatistics(
            common = fertilizing.toCommon(),
            fertilizing = fertilizing,
        )
        WorkType.PEST_CONTROL -> FarmingWorkReportResult.WorkStatistics(
            common = pestControl.toCommon(),
            pestControl = pestControl,
        )
        WorkType.WEEDING -> FarmingWorkReportResult.WorkStatistics(
            common = weeding.toCommon(),
            weeding = weeding,
        )
        WorkType.PRUNING -> FarmingWorkReportResult.WorkStatistics(common = pruning)
        WorkType.HARVEST -> FarmingWorkReportResult.WorkStatistics(
            common = harvest.toCommon(),
            harvest = harvest,
        )
        WorkType.ETC -> FarmingWorkReportResult.WorkStatistics(common = etc)
    }

private fun PlantingStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun WateringStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun FertilizingStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun PestControlStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun WeedingStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun HarvestStatistics.toCommon() = common(
    recordCount,
    firstWorkedOn,
    lastWorkedOn,
    workedDayCount,
    averageIntervalDays,
    photoAttachedRecordCount,
    photoAttachmentRatePct,
    weatherDistribution,
    averageTemperatureC,
)

private fun common(
    recordCount: Int,
    firstWorkedOn: LocalDate?,
    lastWorkedOn: LocalDate?,
    workedDayCount: Int,
    averageIntervalDays: BigDecimal?,
    photoAttachedRecordCount: Int,
    photoAttachmentRatePct: BigDecimal?,
    weatherDistribution: List<com.chamchamcham.domain.report.CountDistribution>,
    averageTemperatureC: BigDecimal?,
) = CommonOnlyStatistics(
    recordCount = recordCount,
    firstWorkedOn = firstWorkedOn,
    lastWorkedOn = lastWorkedOn,
    workedDayCount = workedDayCount,
    averageIntervalDays = averageIntervalDays,
    photoAttachedRecordCount = photoAttachedRecordCount,
    photoAttachmentRatePct = photoAttachmentRatePct,
    weatherDistribution = weatherDistribution,
    averageTemperatureC = averageTemperatureC,
)
