package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.coaching.rag.common.RagAuditStatus
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationResult
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationService
import com.chamchamcham.domain.coaching.RecordFeedback
import com.chamchamcham.domain.coaching.RecordFeedbackNextActionDraft
import com.chamchamcham.domain.coaching.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.RecordFeedbackStatus
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class RecordFeedbackGenerationProcessor(
    private val feedbackRepository: RecordFeedbackRepository,
    private val generationService: RecordFeedbackGenerationService,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun generate(event: RecordFeedbackGenerationRequested) {
        val pendingGeneration = loadPendingGeneration(event) ?: return
        val generationResult = try {
            generationService.generate(pendingGeneration.context)
        } catch (failure: RecordFeedbackGenerationFailure) {
            return finalizeFailure(event, pendingGeneration.snapshot, failure.code)
        } catch (exception: RuntimeException) {
            logger.error(exception) { "unexpected record feedback generation failure" }
            return finalizeFailure(event, pendingGeneration.snapshot, RecordFeedbackFailureCode.UNEXPECTED)
        }

        finalizeReady(event, pendingGeneration.snapshot, generationResult)
    }

    private fun loadPendingGeneration(event: RecordFeedbackGenerationRequested): ValidGeneration? {
        return writeTransaction.execute {
            val feedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return@execute null
            val snapshot = feedback.inputSnapshot ?: return@execute null
            if (!feedback.matches(event, snapshot)) {
                return@execute null
            }

            val context = deserializeSnapshot(snapshot) ?: return@execute MalformedGeneration(snapshot)
            ValidGeneration(context = context, snapshot = snapshot)
        }?.let { generation ->
            when (generation) {
                is ValidGeneration -> generation
                is MalformedGeneration -> {
                    finalizeFailure(
                        event = event,
                        snapshot = generation.snapshot,
                        failureCode = RecordFeedbackFailureCode.INVALID_CONTEXT_SNAPSHOT,
                    )
                    null
                }
            }
        }
    }

    private fun finalizeReady(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
        generationResult: RecordFeedbackGenerationResult,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markReady(
                goodPointBasis = generationResult.content.goodPoint.basis,
                goodPointText = generationResult.content.goodPoint.text,
                nextActions = generationResult.content.nextActions.map {
                    RecordFeedbackNextActionDraft(
                        due = it.due,
                        category = it.category,
                        basis = it.basis,
                        text = it.text,
                    )
                },
                citations = generationResult.citations,
                auditStatus = generationResult.auditStatus(),
                auditWarnings = generationResult.auditWarnings,
                modelName = generationResult.modelInfo.chat,
                embeddingModel = generationResult.modelInfo.embedding,
            )
        }
    }

    private fun finalizeFailure(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
        failureCode: RecordFeedbackFailureCode,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markFailed(failureCode.name)
        }
    }

    private fun RecordFeedback.matches(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
    ): Boolean {
        return status == RecordFeedbackStatus.PENDING &&
            record.id == event.recordId &&
            sourceRevision == event.sourceRevision &&
            inputSnapshot == snapshot
    }

    private fun deserializeSnapshot(snapshot: Map<String, Any?>): RecordFeedbackContext? {
        return try {
            objectMapper.convertValue(snapshot, RecordFeedbackContext::class.java)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun RecordFeedbackGenerationResult.auditStatus(): String {
        return if (auditWarnings.isEmpty()) {
            RagAuditStatus.PASS.name
        } else {
            RagAuditStatus.WARN.name
        }
    }

    private sealed interface GenerationRead

    private data class ValidGeneration(
        val context: RecordFeedbackContext,
        val snapshot: Map<String, Any?>,
    ) : GenerationRead

    private data class MalformedGeneration(
        val snapshot: Map<String, Any?>,
    ) : GenerationRead

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
