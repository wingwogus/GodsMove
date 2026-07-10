package com.chamchamcham.application.report

import java.time.LocalDateTime
import java.util.UUID

data class FarmingCycleReportCursorPayload(
    val endsAt: LocalDateTime,
    val finalHarvestRecordId: UUID,
)
