package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.common.RagModelInfo

data class RecordFeedbackGenerationResult(
    val content: RecordFeedbackContent,
    val citations: List<Map<String, Any?>>,
    val auditWarnings: List<String>,
    val modelInfo: RagModelInfo,
)
