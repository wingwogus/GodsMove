package com.chamchamcham.api.coaching.dto

import com.chamchamcham.application.coaching.feedback.RecordFeedbackStatusResult
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionCategory
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionDue
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContent
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

object RecordFeedbackResponses {
    data class StatusResponse(
        val feedbackId: UUID,
        val recordId: UUID,
        val status: CoachingFeedbackStatus,
        val sourceRevision: Long,
        val inputPrepared: Boolean,
        val failureCode: String?,
        val feedback: FeedbackResponse?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    ) {
        companion object {
            fun from(source: RecordFeedbackStatusResult) = StatusResponse(
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
            fun from(source: RecordFeedbackContent) = FeedbackResponse(
                goodPoint = GoodPointResponse(text = source.goodPoint.text),
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
