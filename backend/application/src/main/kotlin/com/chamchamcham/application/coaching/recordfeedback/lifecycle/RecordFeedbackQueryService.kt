package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RecordFeedbackQueryService(
    private val farmingRecordRepository: FarmingRecordRepository,
    private val feedbackRepository: RecordFeedbackRepository,
    private val lifecycleService: RecordFeedbackLifecycleService,
) {
    @Transactional(readOnly = true)
    fun get(memberId: UUID, recordId: UUID): RecordFeedbackDetailResult {
        val record = findOwnedActiveRecord(memberId, recordId)
        return findLatest(record).toDetailResult()
    }

    @Transactional
    fun regenerate(memberId: UUID, recordId: UUID): RecordFeedbackDetailResult {
        val record = findOwnedActiveRecord(memberId, recordId)
        val current = findLatest(record)

        if (current.status != RecordFeedbackStatus.FAILED) {
            throw BusinessException(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED)
        }

        return lifecycleService.retry(current).toDetailResult()
    }

    private fun findOwnedActiveRecord(memberId: UUID, recordId: UUID): FarmingRecord =
        farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)
            ?.takeUnless(FarmingRecord::isDeleted)
            ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)

    private fun findLatest(record: FarmingRecord): RecordFeedback =
        feedbackRepository.findTopByRecord_IdOrderBySourceRevisionDesc(requireNotNull(record.id))
            ?: throw BusinessException(ErrorCode.RECORD_FEEDBACK_NOT_FOUND)

    private fun RecordFeedback.toDetailResult(): RecordFeedbackDetailResult = RecordFeedbackDetailResult(
        feedbackId = requireNotNull(id),
        recordId = requireNotNull(record.id),
        status = status,
        sourceRevision = sourceRevision,
        inputPrepared = inputSnapshot != null,
        failureCode = failureCode,
        content = toContent(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun RecordFeedback.toContent(): RecordFeedbackResultContent? {
        if (status != RecordFeedbackStatus.READY) {
            return null
        }

        val goodPoint = goodPointText ?: throw IllegalStateException("READY record feedback has no good point")
        val nextActions = nextActions()
        if (nextActions.size !in 2..3) {
            throw IllegalStateException("READY record feedback has invalid next action count")
        }

        return RecordFeedbackResultContent(
            goodPoint = goodPoint,
            nextActions = nextActions.map {
                RecordFeedbackNextActionResult(
                    text = it.text,
                    due = it.due,
                    category = it.category,
                )
            },
        )
    }
}
