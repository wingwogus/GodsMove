package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class ReportFeedbackQueryService(
    private val reportRepository: FarmingCycleReportRepository,
    private val feedbackRepository: ReportFeedbackRepository,
) {
    @Transactional(readOnly = true)
    fun get(memberId: UUID, reportId: UUID): ReportFeedbackDetailResult {
        reportRepository.findByIdAndMember_Id(reportId, memberId) ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)
        val feedback = feedbackRepository.findByReport_IdAndMember_Id(reportId, memberId)
            ?: throw BusinessException(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
        return feedback.toDetailResult()
    }

    private fun ReportFeedback.toDetailResult() = ReportFeedbackDetailResult(
        feedbackId = requireNotNull(id),
        reportId = requireNotNull(report.id),
        status = status,
        inputPrepared = inputSnapshot != null,
        failureCode = failureCode,
        content = if (status == ReportFeedbackStatus.READY) {
            ReportFeedbackResultContent(
                summary = requireNotNull(summary),
                strengths = itemsFor(ReportFeedbackItemSection.STRENGTH),
                improvements = itemsFor(ReportFeedbackItemSection.IMPROVEMENT),
                nextCycleActions = itemsFor(ReportFeedbackItemSection.NEXT_CYCLE_ACTION),
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

data class ReportFeedbackDetailResult(
    val feedbackId: UUID,
    val reportId: UUID,
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
    val nextCycleActions: List<ReportFeedbackItemResult>,
)

data class ReportFeedbackItemResult(
    val text: String,
)
