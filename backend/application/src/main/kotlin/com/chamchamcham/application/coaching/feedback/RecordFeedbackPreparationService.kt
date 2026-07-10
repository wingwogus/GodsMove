package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContextAssembler
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RecordFeedbackPreparationService(
    private val feedbackRepository: CoachingFeedbackRepository,
    private val contextAssembler: RecordFeedbackContextAssembler,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun prepare(event: RecordFeedbackPreparationRequested) {
        val feedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return
        val record = feedback.record ?: return
        if (
            feedback.status != CoachingFeedbackStatus.PENDING ||
            record.id != event.recordId ||
            feedback.sourceRevision != event.sourceRevision
        ) {
            return
        }

        try {
            val context = contextAssembler.assemble(event.memberId, event.recordId)
            val snapshot = objectMapper.convertValue(context, SNAPSHOT_TYPE)
            feedback.attachInputSnapshot(snapshot)
        } catch (_: RuntimeException) {
            feedback.markFailed(CONTEXT_ASSEMBLY_FAILED)
        }
    }

    private companion object {
        const val CONTEXT_ASSEMBLY_FAILED = "CONTEXT_ASSEMBLY_FAILED"
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
