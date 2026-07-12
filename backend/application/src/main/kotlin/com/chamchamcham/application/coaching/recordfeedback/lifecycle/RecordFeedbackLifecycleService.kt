package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.domain.coaching.RecordFeedback
import com.chamchamcham.domain.coaching.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.RecordFeedbackStatus
import com.chamchamcham.domain.farming.FarmingRecord
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RecordFeedbackLifecycleService(
    private val feedbackRepository: RecordFeedbackRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun enqueue(record: FarmingRecord): RecordFeedback {
        val recordId = requireNotNull(record.id) { "Persisted farming record id is required" }
        val current = feedbackRepository.findByRecord_IdAndSourceRevision(recordId, record.sourceRevision)
        if (current != null) {
            return current
        }

        feedbackRepository.findAllByRecord_IdAndStatusIn(recordId, ACTIVE_STATUSES)
            .forEach(RecordFeedback::markStale)

        val saved = feedbackRepository.save(
            RecordFeedback.pending(
                member = record.member,
                record = record,
                sourceRevision = record.sourceRevision,
            ),
        )
        publishPreparation(saved)
        return saved
    }

    @Transactional
    fun staleFor(recordId: UUID) {
        feedbackRepository.findAllByRecord_IdAndStatusIn(recordId, ACTIVE_STATUSES)
            .forEach(RecordFeedback::markStale)
    }

    @Transactional
    fun retry(feedback: RecordFeedback): RecordFeedback {
        feedback.retry()
        val saved = feedbackRepository.save(feedback)
        publishPreparation(saved)
        return saved
    }

    private fun publishPreparation(feedback: RecordFeedback) {
        val feedbackId = requireNotNull(feedback.id) { "Persisted feedback id is required" }
        val memberId = requireNotNull(feedback.member.id) { "Persisted member id is required" }
        val recordId = requireNotNull(feedback.record.id) { "Persisted farming record id is required" }
        eventPublisher.publishEvent(
            RecordFeedbackPreparationRequested(
                feedbackId = feedbackId,
                memberId = memberId,
                recordId = recordId,
                sourceRevision = feedback.sourceRevision,
            ),
        )
    }

    private companion object {
        val ACTIVE_STATUSES = listOf(RecordFeedbackStatus.PENDING, RecordFeedbackStatus.READY)
    }
}
