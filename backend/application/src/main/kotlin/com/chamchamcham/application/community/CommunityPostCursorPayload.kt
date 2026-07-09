package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostSort
import java.time.LocalDateTime
import java.util.UUID

data class CommunityPostCursorPayload(
    val sort: CommunityPostSort,
    val score: Long?,
    val createdAt: LocalDateTime,
    val id: UUID
)
