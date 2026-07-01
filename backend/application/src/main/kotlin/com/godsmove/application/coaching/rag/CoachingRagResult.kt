package com.godsmove.application.coaching.rag

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
    val answer: String,
    val citations: List<RagCitation>,
    val audit: RagAuditResult,
    val model: RagModelInfo
)
