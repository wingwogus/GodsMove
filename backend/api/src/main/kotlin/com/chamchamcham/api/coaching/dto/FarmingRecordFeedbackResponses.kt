package com.chamchamcham.api.coaching.dto

import com.chamchamcham.application.coaching.feedback.RecordFeedbackResult
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

object FarmingRecordFeedbackResponses {
    data class StatusResponse(
        val feedbackId: UUID,
        val recordId: UUID,
        val status: CoachingFeedbackStatus,
        val sourceRevision: Long,
        val inputPrepared: Boolean,
        val failureCode: String?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    ) {
        companion object {
            fun from(source: RecordFeedbackResult) = StatusResponse(
                feedbackId = source.feedbackId,
                recordId = source.recordId,
                status = source.status,
                sourceRevision = source.sourceRevision,
                inputPrepared = source.inputPrepared,
                failureCode = source.failureCode,
                createdAt = source.createdAt,
                updatedAt = source.updatedAt,
            )
        }
    }
}
