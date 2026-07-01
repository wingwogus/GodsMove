package com.godsmove.application.coaching.rag

import java.time.LocalDate
import java.util.UUID

enum class CoachingRagMode {
    CHAT,
    RECORD_AUTO,
    REPORT_MANUAL
}

data class CoachingRagCommand(
    val memberId: UUID,
    val mode: CoachingRagMode = CoachingRagMode.CHAT,
    val question: String,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workTypeId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val topK: Int? = null
)
