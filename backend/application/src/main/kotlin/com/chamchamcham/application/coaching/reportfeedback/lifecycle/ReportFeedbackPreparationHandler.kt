package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssembler
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
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
    val workType: WorkType,
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReportFeedbackPreparationRequested) {
        val feedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return
        if (
            feedback.status != ReportFeedbackStatus.PENDING ||
            feedback.report.id != event.reportId ||
            feedback.workType != event.workType
        ) return
        val snapshotBeforePreparation = feedback.inputSnapshot
        var failureCode: ReportFeedbackFailureCode? = null
        val snapshot = try {
            objectMapper.convertValue(
                contextAssembler.assemble(event.memberId, event.reportId, event.workType),
                SNAPSHOT_TYPE,
            )
        } catch (failure: ReportFeedbackGenerationFailure) {
            failureCode = failure.code
            null
        } catch (_: RuntimeException) {
            failureCode = ReportFeedbackFailureCode.CONTEXT_ASSEMBLY_FAILED
            null
        }
        val generationRequest = writeTransaction.execute {
            val locked = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@execute null
            if (
                locked.status != ReportFeedbackStatus.PENDING ||
                locked.report.id != event.reportId ||
                locked.workType != event.workType ||
                locked.inputSnapshot != snapshotBeforePreparation
            ) return@execute null
            if (snapshot == null) {
                locked.markFailed(requireNotNull(failureCode).name)
                return@execute null
            }
            locked.attachInputSnapshot(snapshot)
            ReportFeedbackGenerationRequested(
                feedbackId = event.feedbackId,
                memberId = event.memberId,
                reportId = event.reportId,
                workType = event.workType,
            )
        }
        generationRequest?.let(eventPublisher::publishEvent)
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
