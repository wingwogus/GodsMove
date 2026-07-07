package com.chamchamcham.application.community

import java.util.UUID

object CommunityCommentCommand {
    data class Create(
        val memberId: UUID,
        val postId: UUID,
        val parentCommentId: UUID?,
        val body: String,
        val mediaId: UUID?
    )

    data class Delete(
        val memberId: UUID,
        val commentId: UUID
    )
}
