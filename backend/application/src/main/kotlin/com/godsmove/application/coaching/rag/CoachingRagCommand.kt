package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
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
