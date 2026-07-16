package com.chamchamcham.domain.community

import java.time.LocalDateTime
import java.util.UUID

interface CommunityPostQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    fun count(condition: SearchCondition): Long

    data class SearchCondition(
        val memberId: UUID?,
        val authorMemberId: UUID? = null,
        val cropId: UUID?,
        val postType: CommunityPostType?,
        val keyword: String?,
        val likedOnly: Boolean,
        val mineOnly: Boolean,
        val sort: CommunityPostSort,
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val sort: CommunityPostSort,
        val score: Long?,
        val createdAt: LocalDateTime,
        val id: UUID
    )

    data class Row(
        val post: CommunityPost,
        val thumbnailUrl: String?,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val score: Long?
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
