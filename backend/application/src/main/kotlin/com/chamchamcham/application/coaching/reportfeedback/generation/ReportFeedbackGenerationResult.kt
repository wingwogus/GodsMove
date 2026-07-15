package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.RagModelInfo

data class ReportFeedbackGenerationResult(
    val content: ReportFeedbackContent,
    val citations: List<Map<String, Any?>>,
    val auditWarnings: List<String>,
    val modelInfo: RagModelInfo,
)
