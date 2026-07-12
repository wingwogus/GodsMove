package com.chamchamcham.application.coaching.recordfeedback

enum class RecordFeedbackFailureCode {
    CONTEXT_ASSEMBLY_FAILED,
    INVALID_CONTEXT_SNAPSHOT,
    INVALID_CONTEXT,
    INVALID_GENERATION_REQUEST,
    INSUFFICIENT_EVIDENCE,
    RETRIEVAL_FAILED,
    CHAT_UNAVAILABLE,
    STRUCTURED_OUTPUT_INVALID,
    UNEXPECTED,
}

class RecordFeedbackGenerationFailure(
    val code: RecordFeedbackFailureCode,
    cause: Throwable? = null,
) : RuntimeException(code.name, cause)
