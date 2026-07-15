package com.chamchamcham.domain.community

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CommunityPostQueryRepositoryImpl(
    private val entityManager: EntityManager
) : CommunityPostQueryRepository {
    override fun search(condition: CommunityPostQueryRepository.SearchCondition): CommunityPostQueryRepository.SearchResult {
        val selectedPosts = findPosts(condition)
        if (selectedPosts.isEmpty()) {
            return CommunityPostQueryRepository.SearchResult(emptyList())
        }

        val postIds = selectedPosts.map { requireNotNull(it.post.id) { "Persisted post id is required" } }
        val thumbnails = findThumbnails(postIds)
        val likedPostIds = findLikedPostIds(postIds, condition.memberId)

        return CommunityPostQueryRepository.SearchResult(
            rows = selectedPosts.map { selectedPost ->
                val postId = requireNotNull(selectedPost.post.id) { "Persisted post id is required" }
                CommunityPostQueryRepository.Row(
                    post = selectedPost.post,
                    thumbnailUrl = thumbnails[postId],
                    commentCount = selectedPost.commentCount,
                    likeCount = selectedPost.likeCount,
                    likedByMe = likedPostIds.contains(postId),
                    score = selectedPost.score
                )
            }
        )
    }

    override fun count(condition: CommunityPostQueryRepository.SearchCondition): Long {
        val (where, params) = buildFilterPredicates(condition)

        val query = entityManager.createQuery(
            """
            select count(p)
            from CommunityPost p
            where ${where.joinToString(" and ")}
            """.trimIndent(),
            Long::class.javaObjectType
        )
        params.forEach(query::setParameter)
        return query.singleResult
    }

    private fun buildFilterPredicates(
        condition: CommunityPostQueryRepository.SearchCondition
    ): Pair<MutableList<String>, MutableMap<String, Any>> {
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

        return where to params
    }

    private fun findPosts(condition: CommunityPostQueryRepository.SearchCondition): List<SelectedPost> {
        val (where, params) = buildFilterPredicates(condition)
        val commentCountExpression = "(select count(c) from CommunityComment c where c.post = p and c.isDeleted = false)"
        val likeCountExpression = "(select count(l) from CommunityPostLike l where l.post = p)"
        val scoreExpression = when (condition.sort) {
            CommunityPostSort.LATEST -> "0"
            CommunityPostSort.LIKE -> likeCountExpression
            CommunityPostSort.COMMENT -> commentCountExpression
            CommunityPostSort.POPULAR -> "($likeCountExpression + $commentCountExpression)"
        }
        val score = when (condition.sort) {
            CommunityPostSort.LATEST -> null
            else -> scoreExpression
        }
        val orderBy = when (condition.sort) {
            CommunityPostSort.LATEST -> "p.createdAt desc, p.id desc"
            else -> "$scoreExpression desc, p.createdAt desc, p.id desc"
        }

        condition.cursor?.let { cursor ->
            when (condition.sort) {
                CommunityPostSort.LATEST -> {
                    where += "(p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))"
                }
                else -> {
                    where += "($scoreExpression < :cursorScore or ($scoreExpression = :cursorScore and p.createdAt < :cursorCreatedAt) or ($scoreExpression = :cursorScore and p.createdAt = :cursorCreatedAt and p.id < :cursorId))"
                    params["cursorScore"] = requireNotNull(cursor.score) {
                        "Cursor score is required for ${condition.sort} sort"
                    }
                }
            }
            params["cursorCreatedAt"] = cursor.createdAt
            params["cursorId"] = cursor.id
        }

        val scoreSelect = score ?: "null"
        val query = entityManager.createQuery(
            """
            select p, $commentCountExpression, $likeCountExpression, $scoreSelect
            from CommunityPost p
            where ${where.joinToString(" and ")}
            order by $orderBy
            """.trimIndent(),
            Array<Any>::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        return query.resultList.map { row ->
            SelectedPost(
                post = row[0] as CommunityPost,
                commentCount = row[1] as Long,
                likeCount = row[2] as Long,
                score = row[3] as Long?
            )
        }
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

    private data class SelectedPost(
        val post: CommunityPost,
        val commentCount: Long,
        val likeCount: Long,
        val score: Long?
    )
}
