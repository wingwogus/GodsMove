package com.chamchamcham.application.policy.recommendation

import java.time.LocalDateTime
import java.util.UUID

data class PolicyRecommendationSearchCursorPayload(
    val createdAt: LocalDateTime,
    val id: UUID,
)
