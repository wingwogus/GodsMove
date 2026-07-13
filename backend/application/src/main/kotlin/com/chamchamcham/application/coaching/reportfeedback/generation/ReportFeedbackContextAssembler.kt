package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.report.FarmingCycleReportSourceLoader
import com.chamchamcham.application.report.ReportScope
import com.chamchamcham.application.report.CycleReportSourceRecord
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReportFeedbackContextAssembler(
    private val reportRepository: FarmingCycleReportRepository,
    private val sourceLoader: FarmingCycleReportSourceLoader,
) {
    @Transactional(readOnly = true)
    fun assemble(memberId: UUID, reportId: UUID): ReportFeedbackContext {
        val report = reportRepository.findByIdAndMember_Id(reportId, memberId)
            ?: throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        if (report.status != FarmingCycleReportStatus.COMPLETED || report.endsAt == null) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        }

        val scope = ReportScope(
            memberId = memberId,
            farmId = requireNotNull(report.farm.id),
            cropId = requireNotNull(report.crop.id),
        )
        val records = sourceLoader.load(scope)
            .filter { !it.workedAt.isBefore(report.startsAt) && !it.workedAt.isAfter(requireNotNull(report.endsAt)) }
            .map {
                ReportFeedbackRecord(
                    id = it.id,
                    workedAt = it.workedAt,
                    workType = it.workType.name,
                    memo = it.memo,
                    details = it.toFeedbackDetails(),
                )
            }
        if (records.isEmpty()) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        }
        val previous = reportRepository.findTopByMember_IdAndFarm_IdAndCrop_IdAndStatusAndEndsAtBeforeAndIdNotOrderByEndsAtDescIdDesc(
            memberId = memberId,
            farmId = requireNotNull(report.farm.id),
            cropId = requireNotNull(report.crop.id),
            status = FarmingCycleReportStatus.COMPLETED,
            beforeEndsAt = requireNotNull(report.endsAt),
            excludedReportId = reportId,
        )

        return ReportFeedbackContext(
            schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
            report = report.toContextReport(),
            records = records,
            previousReport = previous?.toPreviousReport(),
            warnings = if (previous == null) listOf("previous_report_unavailable") else emptyList(),
        )
    }

    private fun FarmingCycleReport.toContextReport() = ReportFeedbackReport(
        id = requireNotNull(id),
        farmName = farm.name,
        cropName = crop.name,
        startsAt = startsAt,
        endsAt = requireNotNull(endsAt),
        statistics = statistics,
    )

    private fun FarmingCycleReport.toPreviousReport() = ReportFeedbackPreviousReport(
        id = requireNotNull(id),
        startsAt = startsAt,
        endsAt = requireNotNull(endsAt),
        statistics = statistics,
    )

    private fun CycleReportSourceRecord.toFeedbackDetails(): Map<String, Any?> = buildMap {
        put("weatherCondition", weatherCondition)
        put("weatherTemperature", weatherTemperature)
        put("hasPhoto", hasPhoto)
        planting?.let { put("planting", it) }
        watering?.let { put("watering", it) }
        fertilizing?.let { put("fertilizing", it) }
        pestControl?.let { put("pestControl", it) }
        weeding?.let { put("weeding", it) }
        harvest?.let { put("harvest", it) }
    }
}
