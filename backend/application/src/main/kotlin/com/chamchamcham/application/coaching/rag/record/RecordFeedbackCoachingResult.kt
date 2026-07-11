package com.chamchamcham.application.coaching.rag.record

data class RecordFeedbackCoachingResult(
    val goodPoint: RecordFeedbackItem,
    val nextActions: List<RecordFeedbackNextAction>,
)

data class RecordFeedbackItem(
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)

data class RecordFeedbackNextAction(
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
