package com.chamchamcham.application.community

import java.time.LocalDateTime
import java.util.UUID

object CommunityCommentResult {
    data class CommentId(val id: UUID)

    data class Page(
        val items: List<Comment>,
        val nextCursor: String?
    )

    data class Comment(
        val id: UUID,
        val parentCommentId: UUID?,
        val author: CommunityPostResult.AuthorSummary,
        val body: String,
        val imageUrl: String?,
        val deleted: Boolean,
        val createdAt: LocalDateTime,
        val replies: List<Comment> = emptyList()
    )
}
