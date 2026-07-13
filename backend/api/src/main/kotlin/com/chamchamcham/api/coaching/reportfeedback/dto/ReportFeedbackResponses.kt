package com.chamchamcham.api.coaching.reportfeedback.dto

import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackDetailResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import java.time.LocalDateTime
import java.util.UUID

object ReportFeedbackResponses {
    data class StatusResponse(
        val feedbackId: UUID,
        val reportId: UUID,
        val status: ReportFeedbackStatus,
        val inputPrepared: Boolean,
        val failureCode: String?,
        val feedback: FeedbackResponse?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    ) {
        companion object {
            fun from(source: ReportFeedbackDetailResult) = StatusResponse(
                feedbackId = source.feedbackId,
                reportId = source.reportId,
                status = source.status,
                inputPrepared = source.inputPrepared,
                failureCode = source.failureCode,
                feedback = source.content?.let(FeedbackResponse::from),
                createdAt = source.createdAt,
                updatedAt = source.updatedAt,
            )
        }
    }

    data class FeedbackResponse(
        val summary: String,
        val strengths: List<ItemResponse>,
        val improvements: List<ItemResponse>,
        val nextCycleActions: List<ItemResponse>,
    ) {
        companion object {
            fun from(source: ReportFeedbackResultContent) = FeedbackResponse(
                summary = source.summary,
                strengths = source.strengths.map(ItemResponse::from),
                improvements = source.improvements.map(ItemResponse::from),
                nextCycleActions = source.nextCycleActions.map(ItemResponse::from),
            )
        }
    }

    data class ItemResponse(
        val text: String,
    ) {
        companion object {
            fun from(source: ReportFeedbackItemResult) = ItemResponse(text = source.text)
        }
    }
}
