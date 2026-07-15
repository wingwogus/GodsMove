package com.chamchamcham.application.coaching.chat

import java.time.LocalDate
import java.util.UUID

data class CoachingRagCommand(
    val memberId: UUID,
    val question: String,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val topK: Int? = null
)
