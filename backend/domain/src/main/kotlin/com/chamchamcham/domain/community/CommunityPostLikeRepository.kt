package com.chamchamcham.domain.community

import com.chamchamcham.domain.member.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostLikeRepository : JpaRepository<CommunityPostLike, UUID> {
    fun findByPostIdAndMemberId(postId: UUID, memberId: UUID): CommunityPostLike?
    fun existsByPostIdAndMemberId(postId: UUID, memberId: UUID): Boolean
    fun countByPostId(postId: UUID): Long
    fun deleteByPostAndMember(post: CommunityPost, member: Member)
}
