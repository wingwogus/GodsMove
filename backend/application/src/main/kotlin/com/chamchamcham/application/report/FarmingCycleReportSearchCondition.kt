package com.chamchamcham.application.report

import java.util.UUID

data class FarmingCycleReportSearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val cursor: String?,
    val size: Int,
)
