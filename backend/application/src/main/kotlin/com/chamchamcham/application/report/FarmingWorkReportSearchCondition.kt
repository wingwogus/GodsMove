package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import java.util.UUID

data class FarmingWorkReportSearchCondition(
    val memberId: UUID,
    val farmIds: Set<UUID> = emptySet(),
    val cropIds: Set<UUID> = emptySet(),
    val workTypes: List<WorkType> = emptyList(),
    val cursor: String?,
    val size: Int,
)
