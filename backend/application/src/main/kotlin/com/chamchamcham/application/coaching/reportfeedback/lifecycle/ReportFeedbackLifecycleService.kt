package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.farming.WorkType
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
    val workType: WorkType,
)

@Service
class ReportFeedbackLifecycleService(
    private val feedbackRepository: ReportFeedbackRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun enqueue(
        report: FarmingCycleReport,
        workTypes: Set<WorkType>,
    ): List<ReportFeedback> {
        require(report.status == FarmingCycleReportStatus.COMPLETED) { "only completed reports can enqueue feedback" }
        val reportId = requireNotNull(report.id) { "Persisted farming cycle report id is required" }
        val memberId = requireNotNull(report.member.id) { "Persisted member id is required" }
        if (feedbackRepository.existsByReport_Id(reportId)) {
            return feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId)
        }

        val feedbacks = WorkType.entries
            .filter(workTypes::contains)
            .map { ReportFeedback.pending(report.member, report, it) }
        val saved = feedbackRepository.saveAll(feedbacks)
        saved.forEach(::publishPreparation)
        return saved
    }

    @Transactional
    fun retry(feedback: ReportFeedback): ReportFeedback {
        feedback.retry()
        val saved = feedbackRepository.save(feedback)
        publishPreparation(saved)
        return saved
    }

    private fun publishPreparation(feedback: ReportFeedback) {
        eventPublisher.publishEvent(
            ReportFeedbackPreparationRequested(
                feedbackId = requireNotNull(feedback.id) { "Persisted report feedback id is required" },
                memberId = requireNotNull(feedback.member.id) { "Persisted member id is required" },
                reportId = requireNotNull(feedback.report.id) { "Persisted report id is required" },
                workType = feedback.workType,
            ),
        )
    }
}
