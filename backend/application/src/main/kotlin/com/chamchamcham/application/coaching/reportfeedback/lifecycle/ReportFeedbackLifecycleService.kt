package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.domain.coaching.ReportFeedback
import com.chamchamcham.domain.coaching.ReportFeedbackRepository
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ReportFeedbackPreparationRequested(
    val feedbackId: UUID,
    val memberId: UUID,
    val reportId: UUID,
)

@Service
class ReportFeedbackLifecycleService(
    private val feedbackRepository: ReportFeedbackRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun enqueue(report: FarmingCycleReport): ReportFeedback {
        require(report.status == FarmingCycleReportStatus.COMPLETED) { "only completed reports can enqueue feedback" }
        val reportId = requireNotNull(report.id) { "Persisted farming cycle report id is required" }
        feedbackRepository.findByReport_Id(reportId)?.let { return it }

        val saved = feedbackRepository.save(ReportFeedback.pending(report.member, report))
        eventPublisher.publishEvent(
            ReportFeedbackPreparationRequested(
                feedbackId = requireNotNull(saved.id) { "Persisted report feedback id is required" },
                memberId = requireNotNull(report.member.id) { "Persisted member id is required" },
                reportId = reportId,
            ),
        )
        return saved
    }
}
