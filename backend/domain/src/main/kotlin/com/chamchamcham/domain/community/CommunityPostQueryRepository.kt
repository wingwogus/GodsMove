package com.chamchamcham.domain.community

import com.chamchamcham.domain.crop.Crop
import java.time.LocalDateTime
import java.util.UUID

interface CommunityPostQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    fun count(condition: SearchCondition): Long

    /** Distinct crops referenced by [authorMemberId]'s non-deleted posts, regardless of whether the
     * member is still currently farming that crop (`MemberCrop`) — used for the profile "기타 작물" filter. */
    fun findDistinctCropsByAuthor(authorMemberId: UUID): List<Crop>

    data class SearchCondition(
        val memberId: UUID?,
        val authorMemberId: UUID? = null,
        val cropIds: List<UUID> = emptyList(),
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
