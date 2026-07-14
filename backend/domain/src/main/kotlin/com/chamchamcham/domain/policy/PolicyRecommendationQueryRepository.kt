package com.chamchamcham.domain.policy

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface PolicyRecommendationQueryRepository {
    fun findPage(condition: SearchCondition): SearchResult

    fun searchByMember(condition: MemberSearchCondition): List<PolicyRecommendation>

    data class SearchCondition(
        val memberId: UUID,
        val source: PolicySource,
        val sourceYear: String,
        val benefitSummary: String?,
        val sort: PolicyRecommendationSort,
        val cursor: Cursor?,
        val size: Int
    )

    data class MemberSearchCondition(
        val memberId: UUID,
        val keyword: String?,
        val cursorCreatedAt: LocalDateTime?,
        val cursorId: UUID?,
        val size: Int,
    )

    data class Cursor(
        val score: BigDecimal?,
        val applyStartsOn: LocalDate?,
        val applyEndsOn: LocalDate?,
        val id: UUID
    )

    data class SearchResult(
        val rows: List<PolicyRecommendation>
    )
}
