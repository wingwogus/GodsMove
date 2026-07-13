package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

data class RecordFeedbackDetailResult(
    val feedbackId: UUID,
    val recordId: UUID,
    val status: RecordFeedbackStatus,
    val sourceRevision: Long,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val content: RecordFeedbackResultContent?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class RecordFeedbackResultContent(
    val goodPoint: String,
    val nextActions: List<RecordFeedbackNextActionResult>,
)

data class RecordFeedbackNextActionResult(
    val text: String,
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
)
