package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import java.util.UUID

data class CommunityPostSearchCondition(
    val memberId: UUID,
    val authorMemberId: UUID? = null,
    val cropId: UUID?,
    val postType: CommunityPostType?,
    val keyword: String?,
    val likedOnly: Boolean,
    val mineOnly: Boolean,
    val sort: CommunityPostSort,
    val cursor: String?,
    val size: Int
)
