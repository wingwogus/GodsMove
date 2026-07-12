package com.chamchamcham.application.coaching.rag.record

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

enum class RecordFeedbackActionDue {
    TODAY,
    THIS_WEEK,
    NEXT_WEEK,
    NEXT_CHECK,
}

enum class RecordFeedbackActionCategory {
    WEATHER,
    PEST_DISEASE,
    IRRIGATION,
    FERTILIZING,
    PEST_CONTROL,
    HARVEST,
    CULTIVATION,
    GENERAL,
}
