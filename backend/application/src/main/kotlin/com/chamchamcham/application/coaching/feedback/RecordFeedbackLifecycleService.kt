package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
import com.chamchamcham.domain.farming.FarmingRecord
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RecordFeedbackLifecycleService(
    private val feedbackRepository: CoachingFeedbackRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun enqueue(record: FarmingRecord): CoachingFeedback {
        val recordId = requireNotNull(record.id) { "Persisted farming record id is required" }
        val current = feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            recordId,
            record.sourceRevision,
        )
        if (current != null) {
            return current
        }

        feedbackRepository.findAllByFeedbackTypeAndRecord_IdAndStatusIn(
            FeedbackType.RECORD,
            recordId,
            ACTIVE_STATUSES,
        ).forEach(CoachingFeedback::markStale)

        val saved = feedbackRepository.save(
            CoachingFeedback.pendingRecord(
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
        feedbackRepository.findAllByFeedbackTypeAndRecord_IdAndStatusIn(
            FeedbackType.RECORD,
            recordId,
            ACTIVE_STATUSES,
        ).forEach(CoachingFeedback::markStale)
    }

    @Transactional
    fun retry(feedback: CoachingFeedback): CoachingFeedback {
        feedback.retry()
        val saved = feedbackRepository.save(feedback)
        publishPreparation(saved)
        return saved
    }

    private fun publishPreparation(feedback: CoachingFeedback) {
        val feedbackId = requireNotNull(feedback.id) { "Persisted feedback id is required" }
        val record = requireNotNull(feedback.record) { "Record feedback requires a record" }
        val memberId = requireNotNull(feedback.member.id) { "Persisted member id is required" }
        val recordId = requireNotNull(record.id) { "Persisted farming record id is required" }
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
        val ACTIVE_STATUSES = listOf(CoachingFeedbackStatus.PENDING, CoachingFeedbackStatus.READY)
    }
}
