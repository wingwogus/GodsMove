package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.common.RagAuditStatus
import com.chamchamcham.application.coaching.rag.record.GeneratedRecordFeedback
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationException
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationFailureCode
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationService
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

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
        val pending = loadPendingGeneration(event) ?: return
        val generated = try {
            generationService.generate(pending.context)
        } catch (exception: RecordFeedbackGenerationException) {
            return finalizeFailure(event, pending.snapshot, exception.code.name)
        }

        finalizeReady(event, pending.snapshot, generated)
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
                        failureCode = RecordFeedbackGenerationFailureCode.STRUCTURED_OUTPUT_INVALID.name,
                    )
                    null
                }
            }
        }
    }

    private fun finalizeReady(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
        generated: GeneratedRecordFeedback,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId) ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markReady(
                structuredResult = objectMapper.convertValue(generated.result, RESULT_TYPE),
                citations = generated.citations,
                auditStatus = generated.auditStatus(),
                auditWarnings = generated.auditWarnings,
                modelName = generated.modelInfo.chat,
                embeddingModel = generated.modelInfo.embedding,
            )
        }
    }

    private fun finalizeFailure(
        event: RecordFeedbackGenerationRequested,
        snapshot: Map<String, Any?>,
        failureCode: String,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId) ?: return@executeWithoutResult
            if (!feedback.matches(event, snapshot)) {
                return@executeWithoutResult
            }

            feedback.markFailed(failureCode)
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

    private fun GeneratedRecordFeedback.auditStatus(): String {
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
        val RESULT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
