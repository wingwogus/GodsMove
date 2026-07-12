package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import java.util.UUID

data class RecordFeedbackGenerationRequested(
    val feedbackId: UUID,
    val memberId: UUID,
    val recordId: UUID,
    val sourceRevision: Long,
)
