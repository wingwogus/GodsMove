package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RecordFeedbackQueryService(
    private val farmingRecordRepository: FarmingRecordRepository,
    private val feedbackRepository: CoachingFeedbackRepository,
    private val lifecycleService: RecordFeedbackLifecycleService,
) {
    @Transactional(readOnly = true)
    fun get(memberId: UUID, recordId: UUID): RecordFeedbackResult {
        val record = findOwnedActiveRecord(memberId, recordId)
        return findCurrentOrStale(record).toResult()
    }

    @Transactional
    fun regenerate(memberId: UUID, recordId: UUID): RecordFeedbackResult {
        val record = findOwnedActiveRecord(memberId, recordId)
        val current = findCurrent(record)

        if (current == null) {
            if (findLatestStale(record) != null) {
                return lifecycleService.enqueue(record).toResult()
            }
            throw BusinessException(ErrorCode.RECORD_FEEDBACK_NOT_FOUND)
        }

        if (current.status != CoachingFeedbackStatus.FAILED) {
            throw BusinessException(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED)
        }

        return lifecycleService.retry(current).toResult()
    }

    private fun findOwnedActiveRecord(memberId: UUID, recordId: UUID): FarmingRecord =
        farmingRecordRepository.findContextSourceByIdAndMemberId(recordId, memberId)
            ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)

    private fun findCurrentOrStale(record: FarmingRecord): CoachingFeedback =
        findCurrent(record)
            ?: findLatestStale(record)
            ?: throw BusinessException(ErrorCode.RECORD_FEEDBACK_NOT_FOUND)

    private fun findCurrent(record: FarmingRecord): CoachingFeedback? =
        feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            requireNotNull(record.id),
            record.sourceRevision,
        )

    private fun findLatestStale(record: FarmingRecord): CoachingFeedback? =
        feedbackRepository.findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
            FeedbackType.RECORD,
            requireNotNull(record.id),
            CoachingFeedbackStatus.STALE,
        )

    private fun CoachingFeedback.toResult(): RecordFeedbackResult = RecordFeedbackResult(
        feedbackId = requireNotNull(id),
        recordId = requireNotNull(record?.id),
        status = status,
        sourceRevision = sourceRevision,
        inputPrepared = inputSnapshot != null,
        failureCode = failureCode,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
