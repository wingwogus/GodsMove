package com.chamchamcham.api.coaching.recordfeedback.dto

import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackDetailResult
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackResultContent
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

object RecordFeedbackResponses {
    data class StatusResponse(
        val feedbackId: UUID,
        val recordId: UUID,
        val status: RecordFeedbackStatus,
        val sourceRevision: Long,
        val inputPrepared: Boolean,
        val failureCode: String?,
        val feedback: FeedbackResponse?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    ) {
        companion object {
            fun from(source: RecordFeedbackDetailResult) = StatusResponse(
                feedbackId = source.feedbackId,
                recordId = source.recordId,
                status = source.status,
                sourceRevision = source.sourceRevision,
                inputPrepared = source.inputPrepared,
                failureCode = source.failureCode,
                feedback = source.content?.let(FeedbackResponse::from),
                createdAt = source.createdAt,
                updatedAt = source.updatedAt,
            )
        }
    }

    data class FeedbackResponse(
        val goodPoint: GoodPointResponse,
        val nextActions: List<NextActionResponse>,
    ) {
        companion object {
            fun from(source: RecordFeedbackResultContent) = FeedbackResponse(
                goodPoint = GoodPointResponse(text = source.goodPoint),
                nextActions = source.nextActions.map { action ->
                    NextActionResponse(
                        text = action.text,
                        due = action.due,
                        category = action.category,
                    )
                },
            )
        }
    }

    data class GoodPointResponse(
        val text: String,
    )

    data class NextActionResponse(
        val text: String,
        val due: RecordFeedbackActionDue,
        val category: RecordFeedbackActionCategory,
    )
}
