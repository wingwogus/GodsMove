package com.chamchamcham.application.report

import java.util.UUID

data class FarmingCycleReportSearchCondition(
    val memberId: UUID,
    val farmIds: Set<UUID> = emptySet(),
    val cropIds: Set<UUID> = emptySet(),
    val cursor: String?,
    val size: Int,
)
