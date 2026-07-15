package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssembler
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextFingerprint
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportRepository
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
    val sourceFingerprint: String,
)

@Service
class ReportFeedbackLifecycleService(
    private val feedbackRepository: ReportFeedbackRepository,
    private val reportRepository: FarmingCycleReportRepository,
    private val contextAssembler: ReportFeedbackContextAssembler,
    private val contextFingerprint: ReportFeedbackContextFingerprint,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun reconcile(
        report: FarmingCycleReport,
        workTypes: Set<WorkType>,
    ): List<ReportFeedback> {
        require(report.status == FarmingCycleReportStatus.COMPLETED) {
            "only completed reports can reconcile feedback"
        }
        val reportId = requireNotNull(report.id) { "Persisted farming cycle report id is required" }
        val memberId = requireNotNull(report.member.id) { "Persisted member id is required" }
        val existing = feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId)
        val orderedWorkTypes = WorkType.entries.filter(workTypes::contains)

        if (existing.isEmpty()) {
            val pending = orderedWorkTypes.map { workType ->
                val context = contextAssembler.assemble(memberId, reportId, workType)
                ReportFeedback.pending(
                    member = report.member,
                    report = report,
                    workType = workType,
                    sourceFingerprint = contextFingerprint.calculate(context),
                )
            }
            return feedbackRepository.saveAll(pending).also { saved ->
                saved.forEach(::publishPreparation)
            }
        }

        val existingByWorkType = existing.associateBy(ReportFeedback::workType)
        val current = orderedWorkTypes.map { workType ->
            existingByWorkType[workType]?.also { feedback ->
                val context = contextAssembler.assemble(memberId, reportId, workType)
                reconcileFingerprint(feedback, contextFingerprint.calculate(context))
            } ?: ReportFeedback.stalePlaceholder(report.member, report, workType)
        }
        val removed = existing
            .filter { it.workType !in workTypes }
            .onEach(ReportFeedback::markStale)

        return feedbackRepository.saveAll(current + removed)
            .sortedBy { it.workType.ordinal }
    }

    @Transactional
    fun regenerate(memberId: UUID, reportId: UUID, workType: WorkType): ReportFeedback {
        val report = reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)
            ?.takeIf { it.status == FarmingCycleReportStatus.COMPLETED }
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)
        if (report.statistics.recordCountFor(workType) == 0) {
            throw BusinessException(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
        }
        val feedback = feedbackRepository.findByReportAndWorkTypeForUpdate(reportId, memberId, workType)
            ?: throw BusinessException(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
        if (feedback.status != ReportFeedbackStatus.FAILED && feedback.status != ReportFeedbackStatus.STALE) {
            throw BusinessException(ErrorCode.REPORT_FEEDBACK_REGENERATION_NOT_ALLOWED)
        }
        val context = contextAssembler.assemble(memberId, report, workType)
        feedback.retry(contextFingerprint.calculate(context))
        publishPreparation(feedback)
        return feedback
    }

    private fun reconcileFingerprint(feedback: ReportFeedback, currentFingerprint: String) {
        val storedFingerprint = feedback.sourceFingerprint
        if (storedFingerprint != null) {
            if (storedFingerprint != currentFingerprint) feedback.markStale()
            return
        }

        val snapshotFingerprint = feedback.inputSnapshot?.let { snapshot ->
            runCatching { contextFingerprint.calculate(snapshot) }.getOrNull()
        }
        if (snapshotFingerprint != null && snapshotFingerprint == currentFingerprint) {
            feedback.backfillSourceFingerprint(snapshotFingerprint)
        } else {
            feedback.markStale()
        }
    }

    private fun publishPreparation(feedback: ReportFeedback) {
        eventPublisher.publishEvent(
            ReportFeedbackPreparationRequested(
                feedbackId = requireNotNull(feedback.id) { "Persisted report feedback id is required" },
                memberId = requireNotNull(feedback.member.id) { "Persisted member id is required" },
                reportId = requireNotNull(feedback.report.id) { "Persisted report id is required" },
                workType = feedback.workType,
                sourceFingerprint = requireNotNull(feedback.sourceFingerprint) {
                    "Pending report feedback source fingerprint is required"
                },
            ),
        )
    }
}
