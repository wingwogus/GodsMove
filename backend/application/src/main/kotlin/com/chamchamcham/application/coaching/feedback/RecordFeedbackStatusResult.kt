package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContent
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

data class RecordFeedbackStatusResult(
    val feedbackId: UUID,
    val recordId: UUID,
    val status: CoachingFeedbackStatus,
    val sourceRevision: Long,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val content: RecordFeedbackContent?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
