package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.report.FarmingCycleReportSourceLoader
import com.chamchamcham.application.report.FarmingCyclePartitioner
import com.chamchamcham.application.report.ReportScope
import com.chamchamcham.application.report.CycleReportSourceRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReportFeedbackContextAssembler(
    private val reportRepository: FarmingCycleReportRepository,
    private val reportQueryRepository: FarmingCycleReportQueryRepository,
    private val sourceLoader: FarmingCycleReportSourceLoader,
    private val partitioner: FarmingCyclePartitioner,
    private val objectMapper: ObjectMapper,
    private val comparisonCalculator: ReportFeedbackComparisonCalculator,
) {
    @Transactional(readOnly = true)
    fun assemble(memberId: UUID, reportId: UUID, workType: WorkType): ReportFeedbackContext {
        val report = reportRepository.findByIdAndMember_Id(reportId, memberId)
            ?: throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        return assemble(memberId, report, workType)
    }

    @Transactional(readOnly = true)
    fun assemble(memberId: UUID, report: FarmingCycleReport, workType: WorkType): ReportFeedbackContext {
        if (
            report.member.id != memberId ||
            report.status != FarmingCycleReportStatus.COMPLETED ||
            report.endsAt == null
        ) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        }

        val scope = ReportScope(
            memberId = memberId,
            farmId = requireNotNull(report.farm.id),
            cropId = requireNotNull(report.crop.id),
        )
        val targetSlice = partitioner.partition(sourceLoader.load(scope)).singleOrNull {
            it.status == FarmingCycleReportStatus.COMPLETED &&
                it.finalHarvestRecordId == report.finalHarvestRecord?.id
        } ?: throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        val records = targetSlice.records
            .filter { it.workType == workType }
            .map {
                ReportFeedbackRecord(
                    id = it.id,
                    workedAt = it.workedAt,
                    workType = it.workType,
                    memo = it.memo,
                    details = it.toFeedbackDetails(),
                )
            }
        if (records.isEmpty()) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        }
        val previous = reportQueryRepository.findPreviousCompleted(
            memberId = memberId,
            farmId = requireNotNull(report.farm.id),
            cropId = requireNotNull(report.crop.id),
            endsAt = requireNotNull(report.endsAt),
            finalHarvestRecordId = requireNotNull(report.finalHarvestRecord?.id),
        )

        val previousContext = previous?.toPreviousReport(workType)
        val comparisons = if (previousContext == null) {
            emptyList()
        } else {
            comparisonCalculator.calculate(workType, report.statistics, requireNotNull(previous).statistics)
        }
        return ReportFeedbackContext(
            schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            workType = workType,
            report = report.toContextReport(workType),
            records = records,
            previousReport = previousContext,
            comparisons = comparisons,
            warnings = when {
                previous == null -> listOf("previous_report_unavailable")
                previousContext == null -> listOf("previous_work_type_unavailable")
                else -> emptyList()
            },
        )
    }

    private fun FarmingCycleReport.toContextReport(workType: WorkType) = ReportFeedbackReport(
        id = requireNotNull(id),
        farmName = farm.name,
        cropName = crop.name,
        startsAt = startsAt,
        endsAt = requireNotNull(endsAt),
        sourceRevision = sourceRevision,
        statistics = statistics.toWorkTypeMap(workType),
    )

    private fun FarmingCycleReport.toPreviousReport(workType: WorkType): ReportFeedbackPreviousReport? {
        if (statistics.recordCountFor(workType) == 0) {
            return null
        }
        val selectedStatistics = statistics.toWorkTypeMap(workType)
        return ReportFeedbackPreviousReport(
            id = requireNotNull(id),
            startsAt = startsAt,
            endsAt = requireNotNull(endsAt),
            sourceRevision = sourceRevision,
            statistics = selectedStatistics,
        )
    }

    private fun CycleReportStatistics.toWorkTypeMap(workType: WorkType): Map<String, Any?> {
        val selected: Any = when (workType) {
            WorkType.PLANTING -> planting
            WorkType.WATERING -> watering
            WorkType.FERTILIZING -> fertilizing
            WorkType.PEST_CONTROL -> pestControl
            WorkType.WEEDING -> weeding
            WorkType.PRUNING -> pruning
            WorkType.HARVEST -> harvest
            WorkType.ETC -> etc
        }
        return objectMapper.convertValue(selected, MAP_TYPE)
    }

    private fun CycleReportSourceRecord.toFeedbackDetails(): Map<String, Any?> = buildMap {
        put("weatherCondition", weatherCondition)
        put("weatherTemperature", weatherTemperature)
        put("hasPhoto", hasPhoto)
        when (workType) {
            WorkType.PLANTING -> planting?.let { put("planting", it.toDetailMap()) }
            WorkType.WATERING -> watering?.let { put("watering", it.toDetailMap()) }
            WorkType.FERTILIZING -> fertilizing?.let { put("fertilizing", it.toDetailMap()) }
            WorkType.PEST_CONTROL -> pestControl?.let { put("pestControl", it.toDetailMap()) }
            WorkType.WEEDING -> weeding?.let { put("weeding", it.toDetailMap()) }
            WorkType.HARVEST -> harvest?.let { put("harvest", it.toDetailMap()) }
            WorkType.PRUNING, WorkType.ETC -> Unit
        }
    }

    private fun Any.toDetailMap(): Map<String, Any?> = objectMapper.convertValue(this, MAP_TYPE)

    private companion object {
        val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
