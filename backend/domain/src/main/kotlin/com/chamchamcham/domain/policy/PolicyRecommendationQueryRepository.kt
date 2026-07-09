package com.chamchamcham.domain.policy

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface PolicyRecommendationQueryRepository {
    fun findPage(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val source: PolicySource,
        val sourceYear: String,
        val benefitSummary: String?,
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val score: BigDecimal,
        val applyEndsOn: LocalDate?,
        val id: UUID
    )

    data class SearchResult(
        val rows: List<PolicyRecommendation>
    )
}
