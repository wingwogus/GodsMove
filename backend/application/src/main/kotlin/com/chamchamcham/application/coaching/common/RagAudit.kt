package com.chamchamcham.application.coaching.common

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
