package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class FarmingWorkReportCursorPayload(
    val version: Int,
    val lastWorkedOn: LocalDate?,
    val status: FarmingCycleReportStatus,
    val sortAt: LocalDateTime,
    val reportId: UUID,
    val workType: WorkType,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}
