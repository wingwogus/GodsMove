package com.chamchamcham.domain.community

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.UUID

interface CommunityCommentRepository : JpaRepository<CommunityComment, UUID> {
    @Query(
        """
        select c
        from CommunityComment c
        join fetch c.author a
        left join fetch a.profileMedia
        left join fetch c.media
        where c.post.id = :postId
          and c.parentComment is null
        order by c.createdAt desc, c.id desc
        """
    )
    fun findRootFirstPage(
        @Param("postId") postId: UUID,
        pageable: Pageable
    ): List<CommunityComment>

    @Query(
        """
        select c
        from CommunityComment c
        join fetch c.author a
        left join fetch a.profileMedia
        left join fetch c.media
        where c.post.id = :postId
          and c.parentComment is null
          and (
            c.createdAt < :cursorCreatedAt
            or (c.createdAt = :cursorCreatedAt and c.id < :cursorId)
          )
        order by c.createdAt desc, c.id desc
        """
    )
    fun findRootPageAfter(
        @Param("postId") postId: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<CommunityComment>

    @Query(
        """
        select c
        from CommunityComment c
        join fetch c.parentComment p
        join fetch c.author a
        left join fetch a.profileMedia
        left join fetch c.media
        where p.id in :parentIds
        order by c.createdAt asc, c.id asc
        """
    )
    fun findRepliesByParentIds(@Param("parentIds") parentIds: Collection<UUID>): List<CommunityComment>

    fun countByPostIdAndIsDeletedFalse(postId: UUID): Long
}
