package com.chamchamcham.domain.community

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CommunityPostQueryRepositoryImpl(
    private val entityManager: EntityManager
) : CommunityPostQueryRepository {
    override fun search(condition: CommunityPostQueryRepository.SearchCondition): CommunityPostQueryRepository.SearchResult {
        val posts = findPosts(condition)
        if (posts.isEmpty()) {
            return CommunityPostQueryRepository.SearchResult(emptyList())
        }

        val postIds = posts.map { requireNotNull(it.id) { "Persisted post id is required" } }
        val thumbnails = findThumbnails(postIds)
        val commentCounts = countComments(postIds)
        val likeCounts = countLikes(postIds)
        val likedPostIds = findLikedPostIds(postIds, condition.memberId)

        return CommunityPostQueryRepository.SearchResult(
            rows = posts.map { post ->
                val postId = requireNotNull(post.id) { "Persisted post id is required" }
                CommunityPostQueryRepository.Row(
                    post = post,
                    thumbnailUrl = thumbnails[postId],
                    commentCount = commentCounts[postId] ?: 0L,
                    likeCount = likeCounts[postId] ?: 0L,
                    likedByMe = likedPostIds.contains(postId)
                )
            }
        )
    }

    private fun findPosts(condition: CommunityPostQueryRepository.SearchCondition): List<CommunityPost> {
        val where = mutableListOf("p.isDeleted = false")
        val params = mutableMapOf<String, Any>()

        condition.cropId?.let {
            where += "p.crop.id = :cropId"
            params["cropId"] = it
        }
        condition.postType?.let {
            where += "p.postType = :postType"
            params["postType"] = it
        }
        condition.keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let {
            where += "(lower(p.title) like :keyword or lower(p.body) like :keyword)"
            params["keyword"] = "%$it%"
        }
        if (condition.mineOnly) {
            where += "p.author.id = :memberId"
            params["memberId"] = condition.memberId
        }
        if (condition.likedOnly) {
            where += "exists (select 1 from CommunityPostLike l where l.post = p and l.member.id = :memberId)"
            params["memberId"] = condition.memberId
        }
        if (condition.cursorCreatedAt != null && condition.cursorId != null) {
            where += "(p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))"
            params["cursorCreatedAt"] = condition.cursorCreatedAt
            params["cursorId"] = condition.cursorId
        }

        val query = entityManager.createQuery(
            """
            select p
            from CommunityPost p
            where ${where.joinToString(" and ")}
            order by p.createdAt desc, p.id desc
            """.trimIndent(),
            CommunityPost::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        return query.resultList
    }

    private fun findThumbnails(postIds: List<UUID>): Map<UUID, String> {
        val media = entityManager.createQuery(
            """
            select m
            from CommunityPostMedia m
            where m.post.id in :postIds
            order by m.displayOrder asc
            """.trimIndent(),
            CommunityPostMedia::class.java
        )
            .setParameter("postIds", postIds)
            .resultList

        return media
            .groupBy { requireNotNull(it.post.id) { "Persisted post id is required" } }
            .mapValues { (_, rows) -> rows.first().uploadedMedia.fileUrl }
    }

    private fun countComments(postIds: List<UUID>): Map<UUID, Long> =
        aggregateCount(
            """
            select c.post.id, count(c)
            from CommunityComment c
            where c.post.id in :postIds and c.isDeleted = false
            group by c.post.id
            """.trimIndent(),
            postIds
        )

    private fun countLikes(postIds: List<UUID>): Map<UUID, Long> =
        aggregateCount(
            """
            select l.post.id, count(l)
            from CommunityPostLike l
            where l.post.id in :postIds
            group by l.post.id
            """.trimIndent(),
            postIds
        )

    private fun aggregateCount(queryString: String, postIds: List<UUID>): Map<UUID, Long> {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createQuery(queryString)
            .setParameter("postIds", postIds)
            .resultList as List<Array<Any>>
        return rows.associate { row -> row[0] as UUID to row[1] as Long }
    }

    private fun findLikedPostIds(postIds: List<UUID>, memberId: UUID): Set<UUID> {
        return entityManager.createQuery(
            """
            select l.post.id
            from CommunityPostLike l
            where l.post.id in :postIds and l.member.id = :memberId
            """.trimIndent(),
            UUID::class.java
        )
            .setParameter("postIds", postIds)
            .setParameter("memberId", memberId)
            .resultList
            .toSet()
    }
}
