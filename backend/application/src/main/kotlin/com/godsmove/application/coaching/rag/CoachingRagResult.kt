package com.godsmove.application.coaching.rag

import java.util.UUID

enum class RagAuditStatus {
    PASS,
    WARN,
    FAIL
}

data class RagAuditResult(
    val status: RagAuditStatus,
    val warnings: List<String>,
    val citations: List<String>
)

data class RagModelInfo(
    val embedding: String,
    val chat: String
)

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
