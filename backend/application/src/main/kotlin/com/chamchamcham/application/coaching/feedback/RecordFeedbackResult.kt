package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

data class RecordFeedbackResult(
    val feedbackId: UUID,
    val recordId: UUID,
    val status: CoachingFeedbackStatus,
    val sourceRevision: Long,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
