package com.chamchamcham.domain.community

import com.chamchamcham.domain.member.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostLikeRepository : JpaRepository<CommunityPostLike, UUID> {
    fun findByPost_IdAndMember_Id(postId: UUID, memberId: UUID): CommunityPostLike?
    fun existsByPost_IdAndMember_Id(postId: UUID, memberId: UUID): Boolean
    fun countByPost_Id(postId: UUID): Long
    fun deleteByPostAndMember(post: CommunityPost, member: Member)
}
