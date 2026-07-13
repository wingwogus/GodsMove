package com.chamchamcham.application.coaching.chat

import com.chamchamcham.application.coaching.common.RagAuditResult
import com.chamchamcham.application.coaching.common.RagModelInfo
import java.util.UUID

data class CoachingRagResult(
    val result: CoachingStructuredResult,
    val audit: RagAuditResult,
    val model: RagModelInfo,
    val savedFeedbackId: UUID? = null
) {
    val answer: String
        get() = result.summary

    val citations: List<CoachingCitationRef>
        get() = result.citations
}
