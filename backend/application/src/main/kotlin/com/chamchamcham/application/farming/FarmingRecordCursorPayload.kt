package com.chamchamcham.application.farming

import java.time.LocalDateTime
import java.util.UUID

data class FarmingRecordCursorPayload(
    val workedAt: LocalDateTime,
    val id: UUID
)
