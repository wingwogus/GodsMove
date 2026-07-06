package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityCommentRepository : JpaRepository<CommunityComment, UUID> {
    fun findByPost_IdOrderByCreatedAtAscIdAsc(postId: UUID): List<CommunityComment>
    fun countByPost_IdAndIsDeletedFalse(postId: UUID): Long
}
