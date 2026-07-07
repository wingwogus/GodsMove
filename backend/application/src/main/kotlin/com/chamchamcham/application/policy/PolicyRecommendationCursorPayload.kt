package com.chamchamcham.application.policy

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class PolicyRecommendationCursorPayload(
    val sourceSyncJobId: UUID,
    val score: BigDecimal,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
