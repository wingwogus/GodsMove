package com.chamchamcham.domain.policy

import java.time.LocalDateTime
import java.util.UUID

interface PolicyProgramQueryRepository {
    fun search(condition: SearchCondition): List<PolicyProgram>

    fun count(keyword: String?): Long

    data class SearchCondition(
        val keyword: String?,
        val cursorCreatedAt: LocalDateTime?,
        val cursorId: UUID?,
        val size: Int
    )
}
