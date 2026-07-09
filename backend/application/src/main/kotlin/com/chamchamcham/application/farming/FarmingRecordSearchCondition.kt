package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType
import java.time.LocalDate
import java.util.UUID

data class FarmingRecordSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val workType: WorkType?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val cursor: String?,
    val size: Int
)
