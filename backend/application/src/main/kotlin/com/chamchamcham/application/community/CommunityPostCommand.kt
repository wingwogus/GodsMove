package com.chamchamcham.application.community

import com.chamchamcham.domain.community.CommunityPostType
import java.util.UUID

object CommunityPostCommand {
    data class Create(
        val memberId: UUID,
        val cropId: UUID,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val farmingRecordId: UUID?,
        val mediaIds: List<UUID>
    )

    data class Update(
        val memberId: UUID,
        val postId: UUID,
        val cropId: UUID,
        val postType: CommunityPostType,
        val title: String,
        val body: String,
        val farmingRecordId: UUID?,
        val mediaIds: List<UUID>
    )

    data class Delete(
        val memberId: UUID,
        val postId: UUID
    )

    data class ToggleLike(
        val memberId: UUID,
        val postId: UUID
    )
}
