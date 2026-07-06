package com.chamchamcham.domain.community

import java.time.LocalDateTime
import java.util.UUID

interface CommunityPostQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
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

    data class Row(
        val post: CommunityPost,
        val thumbnailUrl: String?,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
