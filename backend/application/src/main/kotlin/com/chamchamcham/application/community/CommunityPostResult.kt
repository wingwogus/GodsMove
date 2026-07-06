package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostType
import java.time.LocalDateTime
import java.util.UUID

object CommunityPostResult {
    data class PostId(val id: UUID)

    data class LikeToggle(
        val liked: Boolean,
        val likeCount: Long
    )

    data class AuthorSummary(
        val memberId: UUID,
        val nickname: String?,
        val profileImageUrl: String?
    )

    data class PostSummary(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val bodyPreview: String,
        val thumbnailUrl: String?,
        val author: AuthorSummary,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    )

    data class PostDetail(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val imageUrls: List<String>,
        val farmingRecordId: UUID?,
        val author: AuthorSummary,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    )

    data class Page(
        val items: List<PostSummary>,
        val nextCursor: String?
    )

    data class Board(
        val cropId: UUID,
        val cropName: String
    )

    fun idOf(post: CommunityPost): PostId =
        PostId(requireNotNull(post.id) { "Persisted post id is required" })
}
