package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType
import java.util.UUID

object FarmingRecordResult {
    data class Detail(
        val id: UUID,
        val workType: WorkType,
    )
}
