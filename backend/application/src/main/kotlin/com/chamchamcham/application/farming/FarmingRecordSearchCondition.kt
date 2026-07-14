package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDate
import java.util.UUID

data class FarmingRecordSearchCondition(
    val memberId: UUID,
    val cropIds: List<UUID> = emptyList(),
    val workTypes: List<WorkType> = emptyList(),
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val keyword: String? = null,
    val cursor: String?,
    val size: Int
)
