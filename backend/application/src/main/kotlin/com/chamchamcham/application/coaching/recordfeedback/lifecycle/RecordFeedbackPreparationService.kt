package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContextAssembler
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class RecordFeedbackPreparationService(
    private val feedbackRepository: RecordFeedbackRepository,
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
        if (currentFeedback.status != RecordFeedbackStatus.PENDING || currentFeedback.sourceRevision != event.sourceRevision) {
            return
        }

        val snapshotResult = try {
            val context = contextAssembler.assemble(event.memberId, event.recordId, event.sourceRevision)
            Result.success(objectMapper.convertValue(context, SNAPSHOT_TYPE))
        } catch (exception: RuntimeException) {
            Result.failure(exception)
        }

        val generationRequest = writeTransaction.execute {
            val lockedFeedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@execute null
            if (!lockedFeedback.matches(event)) {
                return@execute null
            }

            snapshotResult.fold(
                onSuccess = { snapshot ->
                    lockedFeedback.attachInputSnapshot(snapshot)
                    RecordFeedbackGenerationRequested(
                        feedbackId = event.feedbackId,
                        memberId = event.memberId,
                        recordId = event.recordId,
                        sourceRevision = event.sourceRevision,
                    )
                },
                onFailure = {
                    lockedFeedback.markFailed(RecordFeedbackFailureCode.CONTEXT_ASSEMBLY_FAILED.name)
                    null
                },
            )
        }
        generationRequest?.let(eventPublisher::publishEvent)
    }

    private fun RecordFeedback.matches(event: RecordFeedbackPreparationRequested): Boolean {
        return status == RecordFeedbackStatus.PENDING &&
            record.id == event.recordId &&
            sourceRevision == event.sourceRevision
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
