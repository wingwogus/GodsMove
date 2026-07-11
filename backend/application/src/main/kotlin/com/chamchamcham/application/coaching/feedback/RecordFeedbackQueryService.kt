package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.RecordFeedbackCoachingResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RecordFeedbackQueryService(
    private val farmingRecordRepository: FarmingRecordRepository,
    private val feedbackRepository: CoachingFeedbackRepository,
    private val lifecycleService: RecordFeedbackLifecycleService,
    private val objectMapper: ObjectMapper,
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
                throw BusinessException(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED)
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
        feedback = toUserFeedback(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CoachingFeedback.toUserFeedback(): RecordFeedbackUserResponse? {
        if (status != CoachingFeedbackStatus.READY) {
            return null
        }

        val result = try {
            objectMapper.convertValue(
                structuredResult ?: throw IllegalStateException("READY record feedback has no structured result"),
                RecordFeedbackCoachingResult::class.java,
            )
        } catch (exception: IllegalStateException) {
            throw exception
        } catch (exception: RuntimeException) {
            throw IllegalStateException("READY record feedback has malformed structured result", exception)
        }

        return RecordFeedbackUserResponse(
            goodPoint = RecordFeedbackUserGoodPoint(text = result.goodPoint.text),
            nextActions = result.nextActions.map { action ->
                RecordFeedbackUserNextAction(
                    text = action.text,
                    due = action.due,
                    category = action.category,
                )
            },
        )
    }
}
