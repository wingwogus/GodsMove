package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostMediaRepository : JpaRepository<CommunityPostMedia, UUID> {
    fun findByPostIdOrderByDisplayOrderAsc(postId: UUID): List<CommunityPostMedia>
    fun findByUploadedMediaIdIn(mediaIds: Collection<UUID>): List<CommunityPostMedia>
    fun deleteByPost(post: CommunityPost)
}
