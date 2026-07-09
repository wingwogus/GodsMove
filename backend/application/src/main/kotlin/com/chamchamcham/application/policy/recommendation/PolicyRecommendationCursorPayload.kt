package com.chamchamcham.application.policy.recommendation

import com.chamchamcham.domain.policy.PolicySource
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class PolicyRecommendationCursorPayload(
    val source: PolicySource,
    val sourceYear: String,
    val benefitCategory: String?,
    val score: BigDecimal,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
