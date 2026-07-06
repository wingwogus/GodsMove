package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostType
import java.time.LocalDateTime
import java.util.UUID

data class CommunityPostSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val postType: CommunityPostType?,
    val keyword: String?,
    val likedOnly: Boolean,
    val mineOnly: Boolean,
    val cursorCreatedAt: LocalDateTime?,
    val cursorId: UUID?,
    val size: Int
)
