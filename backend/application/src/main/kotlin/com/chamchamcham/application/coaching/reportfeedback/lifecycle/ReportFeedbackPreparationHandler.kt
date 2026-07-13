package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssembler
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class ReportFeedbackGenerationRequested(
    val feedbackId: UUID,
    val memberId: UUID,
    val reportId: UUID,
)

@Component
class ReportFeedbackPreparationHandler(
    private val feedbackRepository: ReportFeedbackRepository,
    private val contextAssembler: ReportFeedbackContextAssembler,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReportFeedbackPreparationRequested) {
        val feedback = feedbackRepository.findByReport_IdAndMember_Id(event.reportId, event.memberId) ?: return
        if (feedback.status != ReportFeedbackStatus.PENDING) return
        val snapshot = runCatching {
            objectMapper.convertValue(
                contextAssembler.assemble(event.memberId, event.reportId, feedback.workType),
                SNAPSHOT_TYPE,
            )
        }
        writeTransaction.executeWithoutResult {
            val locked = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (locked.status != ReportFeedbackStatus.PENDING || locked.report.id != event.reportId) return@executeWithoutResult
            snapshot.fold(
                onSuccess = {
                    locked.attachInputSnapshot(it)
                    eventPublisher.publishEvent(ReportFeedbackGenerationRequested(event.feedbackId, event.memberId, event.reportId))
                },
                onFailure = { locked.markFailed(ReportFeedbackFailureCode.CONTEXT_ASSEMBLY_FAILED.name) },
            )
        }
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
