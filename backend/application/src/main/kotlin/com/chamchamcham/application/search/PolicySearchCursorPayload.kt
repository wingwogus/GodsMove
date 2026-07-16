package com.chamchamcham.application.search

import java.time.LocalDateTime
import java.util.UUID

data class PolicySearchCursorPayload(
    val createdAt: LocalDateTime,
    val id: UUID,
)
