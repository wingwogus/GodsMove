package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class ReportFeedbackQueryService(
    private val reportRepository: FarmingCycleReportRepository,
    private val feedbackRepository: ReportFeedbackRepository,
    private val lifecycleService: ReportFeedbackLifecycleService,
) {
    @Transactional(readOnly = true)
    fun get(memberId: UUID, reportId: UUID): ReportFeedbackListResult {
        val report = findCompletedReport(memberId, reportId)
        return ReportFeedbackListResult(
            reportId = requireNotNull(report.id),
            feedbacks = feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId)
                .filter { report.statistics.recordCountFor(it.workType) > 0 }
                .sortedWith(
                    compareByDescending<ReportFeedback> {
                        report.statistics.lastWorkedOnFor(it.workType) ?: LocalDate.MIN
                    }.thenBy { it.workType.ordinal },
                )
                .map { it.toDetailResult() },
        )
    }

    @Transactional
    fun regenerate(
        memberId: UUID,
        reportId: UUID,
        workType: WorkType,
    ): ReportFeedbackDetailResult {
        return lifecycleService.regenerate(memberId, reportId, workType).toDetailResult()
    }

    private fun findCompletedReport(memberId: UUID, reportId: UUID) =
        reportRepository.findByIdAndMember_Id(reportId, memberId)
            ?.takeIf { it.status == FarmingCycleReportStatus.COMPLETED }
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)

    private fun ReportFeedback.toDetailResult() = ReportFeedbackDetailResult(
        feedbackId = requireNotNull(id),
        workType = workType,
        status = status,
        inputPrepared = inputSnapshot != null,
        failureCode = failureCode,
        content = if (status == ReportFeedbackStatus.READY) {
            ReportFeedbackResultContent(
                summary = requireNotNull(summary),
                comparisons = itemsFor(ReportFeedbackItemSection.COMPARISON),
                strengths = itemsFor(ReportFeedbackItemSection.STRENGTH),
                improvements = itemsFor(ReportFeedbackItemSection.IMPROVEMENT),
                nextActions = itemsFor(ReportFeedbackItemSection.NEXT_ACTION),
            )
        } else {
            null
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ReportFeedback.itemsFor(section: ReportFeedbackItemSection) = items()
        .filter { it.section == section }
        .map { ReportFeedbackItemResult(it.text) }
}

data class ReportFeedbackListResult(
    val reportId: UUID,
    val feedbacks: List<ReportFeedbackDetailResult>,
)

data class ReportFeedbackDetailResult(
    val feedbackId: UUID,
    val workType: WorkType,
    val status: ReportFeedbackStatus,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val content: ReportFeedbackResultContent?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ReportFeedbackResultContent(
    val summary: String,
    val strengths: List<ReportFeedbackItemResult>,
    val improvements: List<ReportFeedbackItemResult>,
    val nextActions: List<ReportFeedbackItemResult>,
    val comparisons: List<ReportFeedbackItemResult> = emptyList(),
)

data class ReportFeedbackItemResult(
    val text: String,
)
