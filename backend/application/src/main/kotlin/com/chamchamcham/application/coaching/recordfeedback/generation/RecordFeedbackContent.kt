package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue

data class RecordFeedbackContent(
    val goodPoint: RecordFeedbackGoodPoint,
    val nextActions: List<RecordFeedbackAction>,
)

data class RecordFeedbackGoodPoint(
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)

data class RecordFeedbackAction(
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)
