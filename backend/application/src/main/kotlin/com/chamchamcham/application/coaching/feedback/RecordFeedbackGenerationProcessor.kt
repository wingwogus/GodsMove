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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RecordFeedbackGenerationProcessor(
    private val feedbackRepository: CoachingFeedbackRepository,
    private val generationService: RecordFeedbackGenerationService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun generate(event: RecordFeedbackGenerationRequested) {
        val feedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return
        val record = feedback.record ?: return
        val snapshot = feedback.inputSnapshot ?: return
        if (
            feedback.status != CoachingFeedbackStatus.PENDING ||
            record.id != event.recordId ||
            feedback.sourceRevision != event.sourceRevision
        ) {
            return
        }

        val context = deserializeSnapshot(snapshot) ?: return feedback.markFailed(
            RecordFeedbackGenerationFailureCode.STRUCTURED_OUTPUT_INVALID.name,
        )

        val generated = try {
            generationService.generate(context)
        } catch (exception: RecordFeedbackGenerationException) {
            return feedback.markFailed(exception.code.name)
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

    private companion object {
        val RESULT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
