package com.chamchamcham.api.community.dto

import com.chamchamcham.application.community.CommunityCommentResult
import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.domain.community.CommunityPostType
import java.time.LocalDateTime
import java.util.UUID

object CommunityResponses {
    data class PostIdResponse(val id: UUID) {
        companion object {
            fun from(result: CommunityPostResult.PostId): PostIdResponse = PostIdResponse(result.id)
        }
    }

    data class CommentIdResponse(val id: UUID) {
        companion object {
            fun from(result: CommunityCommentResult.CommentId): CommentIdResponse = CommentIdResponse(result.id)
        }
    }

    data class LikeToggleResponse(val liked: Boolean, val likeCount: Long) {
        companion object {
            fun from(result: CommunityPostResult.LikeToggle): LikeToggleResponse =
                LikeToggleResponse(liked = result.liked, likeCount = result.likeCount)
        }
    }

    data class BoardResponse(val cropId: UUID, val cropName: String) {
        companion object {
            fun from(result: CommunityPostResult.Board): BoardResponse =
                BoardResponse(cropId = result.cropId, cropName = result.cropName)
        }
    }

    data class AuthorResponse(
        val memberId: UUID,
        val nickname: String?,
        val profileImageUrl: String?
    ) {
        companion object {
            fun from(result: CommunityPostResult.AuthorSummary): AuthorResponse =
                AuthorResponse(
                    memberId = result.memberId,
                    nickname = result.nickname,
                    profileImageUrl = result.profileImageUrl
                )
        }
    }

    data class PostPageResponse(
        val items: List<PostSummaryResponse>,
        val nextCursor: String?
    ) {
        companion object {
            fun from(result: CommunityPostResult.Page): PostPageResponse =
                PostPageResponse(
                    items = result.items.map(PostSummaryResponse::from),
                    nextCursor = result.nextCursor
                )
        }
    }

    data class PostSummaryResponse(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val bodyPreview: String,
        val thumbnailUrl: String?,
        val author: AuthorResponse,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    ) {
        companion object {
            fun from(result: CommunityPostResult.PostSummary): PostSummaryResponse =
                PostSummaryResponse(
                    id = result.id,
                    cropId = result.cropId,
                    cropName = result.cropName,
                    postType = result.postType,
                    title = result.title,
                    bodyPreview = result.bodyPreview,
                    thumbnailUrl = result.thumbnailUrl,
                    author = AuthorResponse.from(result.author),
                    commentCount = result.commentCount,
                    likeCount = result.likeCount,
                    likedByMe = result.likedByMe,
                    createdAt = result.createdAt
                )
        }
    }

    data class PostDetailResponse(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val imageUrls: List<String>,
        val farmingRecordId: UUID?,
        val author: AuthorResponse,
        val commentCount: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val createdAt: LocalDateTime
    ) {
        companion object {
            fun from(result: CommunityPostResult.PostDetail): PostDetailResponse =
                PostDetailResponse(
                    id = result.id,
                    cropId = result.cropId,
                    cropName = result.cropName,
                    postType = result.postType,
                    title = result.title,
                    body = result.body,
                    imageUrls = result.imageUrls,
                    farmingRecordId = result.farmingRecordId,
                    author = AuthorResponse.from(result.author),
                    commentCount = result.commentCount,
                    likeCount = result.likeCount,
                    likedByMe = result.likedByMe,
                    createdAt = result.createdAt
                )
        }
    }

    data class CommentPageResponse(
        val items: List<CommentResponse>,
        val nextCursor: String?
    ) {
        companion object {
            fun from(result: CommunityCommentResult.Page): CommentPageResponse =
                CommentPageResponse(
                    items = result.items.map(CommentResponse::from),
                    nextCursor = result.nextCursor
                )
        }
    }

    data class CommentResponse(
        val id: UUID,
        val parentCommentId: UUID?,
        val author: AuthorResponse,
        val body: String,
        val imageUrl: String?,
        val deleted: Boolean,
        val createdAt: LocalDateTime,
        val replies: List<CommentResponse>
    ) {
        companion object {
            fun from(result: CommunityCommentResult.Comment): CommentResponse =
                CommentResponse(
                    id = result.id,
                    parentCommentId = result.parentCommentId,
                    author = AuthorResponse.from(result.author),
                    body = result.body,
                    imageUrl = result.imageUrl,
                    deleted = result.deleted,
                    createdAt = result.createdAt,
                    replies = result.replies.map(::from)
                )
        }
    }
}
