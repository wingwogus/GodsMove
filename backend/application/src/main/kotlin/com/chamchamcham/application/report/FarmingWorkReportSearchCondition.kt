package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import java.util.UUID

data class FarmingWorkReportSearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val workType: WorkType?,
    val cursor: String?,
    val size: Int,
)
