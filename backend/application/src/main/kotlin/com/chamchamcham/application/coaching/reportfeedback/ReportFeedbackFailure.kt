package com.chamchamcham.application.coaching.reportfeedback

enum class ReportFeedbackFailureCode {
    CONTEXT_ASSEMBLY_FAILED,
    INVALID_CONTEXT_SNAPSHOT,
    INVALID_CONTEXT,
    RETRIEVAL_FAILED,
    CHAT_UNAVAILABLE,
    STRUCTURED_OUTPUT_INVALID,
    UNEXPECTED,
}

class ReportFeedbackGenerationFailure(
    val code: ReportFeedbackFailureCode,
    cause: Throwable? = null,
) : RuntimeException(code.name, cause)
