package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContextAssembler
import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class RecordFeedbackPreparationService(
    private val feedbackRepository: CoachingFeedbackRepository,
    private val contextAssembler: RecordFeedbackContextAssembler,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun prepare(event: RecordFeedbackPreparationRequested) {
        val currentFeedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return
        if (currentFeedback.status != CoachingFeedbackStatus.PENDING || currentFeedback.sourceRevision != event.sourceRevision) {
            return
        }

        val snapshotResult = try {
            val context = contextAssembler.assemble(event.memberId, event.recordId)
            Result.success(objectMapper.convertValue(context, SNAPSHOT_TYPE))
        } catch (exception: RuntimeException) {
            Result.failure(exception)
        }

        writeTransaction.executeWithoutResult {
            val lockedFeedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (!lockedFeedback.matches(event)) {
                return@executeWithoutResult
            }

            snapshotResult.fold(
                onSuccess = { snapshot ->
                    lockedFeedback.attachInputSnapshot(snapshot)
                    eventPublisher.publishEvent(
                        RecordFeedbackGenerationRequested(
                            feedbackId = event.feedbackId,
                            memberId = event.memberId,
                            recordId = event.recordId,
                            sourceRevision = event.sourceRevision,
                        ),
                    )
                },
                onFailure = {
                    lockedFeedback.markFailed(RecordFeedbackFailureCode.CONTEXT_ASSEMBLY_FAILED.name)
                },
            )
        }
    }

    private fun CoachingFeedback.matches(event: RecordFeedbackPreparationRequested): Boolean {
        return status == CoachingFeedbackStatus.PENDING &&
            record?.id == event.recordId &&
            sourceRevision == event.sourceRevision
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
