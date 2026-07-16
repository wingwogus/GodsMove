package com.chamchamcham.api.coaching.reportfeedback.dto

import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackDetailResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackListResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDateTime
import java.util.UUID

object ReportFeedbackResponses {
    data class ListResponse(
        val reportId: UUID,
        val feedbacks: List<StatusResponse>,
    ) {
        companion object {
            fun from(source: ReportFeedbackListResult) = ListResponse(
                reportId = source.reportId,
                feedbacks = source.feedbacks.map(StatusResponse::from),
            )
        }
    }

    data class StatusResponse(
        val feedbackId: UUID,
        val workType: WorkType,
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
                workType = source.workType,
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
        val comparisons: List<ItemResponse>,
        val strengths: List<ItemResponse>,
        val improvements: List<ItemResponse>,
        val nextActions: List<ItemResponse>,
    ) {
        companion object {
            fun from(source: ReportFeedbackResultContent) = FeedbackResponse(
                summary = source.summary,
                comparisons = source.comparisons.map(ItemResponse::from),
                strengths = source.strengths.map(ItemResponse::from),
                improvements = source.improvements.map(ItemResponse::from),
                nextActions = source.nextActions.map(ItemResponse::from),
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
