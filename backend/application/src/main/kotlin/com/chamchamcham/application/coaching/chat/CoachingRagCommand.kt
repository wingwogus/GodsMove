package com.chamchamcham.application.coaching.chat

import com.chamchamcham.domain.coaching.chat.CoachingMode
import java.time.LocalDate
import java.util.UUID

data class CoachingRagCommand(
    val memberId: UUID,
    val mode: CoachingMode = CoachingMode.CHAT,
    val question: String,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workTypeId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val topK: Int? = null
)
