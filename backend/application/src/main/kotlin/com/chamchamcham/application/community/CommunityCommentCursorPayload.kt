package com.chamchamcham.application.community

import java.time.LocalDateTime
import java.util.UUID

data class CommunityCommentCursorPayload(
    val createdAt: LocalDateTime,
    val id: UUID
)
