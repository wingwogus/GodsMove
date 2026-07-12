package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.common.RagAuditStatus
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationResult
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationService
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import mu.KotlinLogging

@Service
class RecordFeedbackGenerationProcessor(
    private val feedbackRepository: CoachingFeedbackRepository,
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
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId) ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markReady(
                structuredResult = objectMapper.convertValue(generationResult.content, RESULT_TYPE),
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
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId) ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markFailed(failureCode.name)
        }
    }

    private fun com.chamchamcham.domain.coaching.CoachingFeedback.matches(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
    ): Boolean {
        val record = record ?: return false
        return status == CoachingFeedbackStatus.PENDING &&
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
        val RESULT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
