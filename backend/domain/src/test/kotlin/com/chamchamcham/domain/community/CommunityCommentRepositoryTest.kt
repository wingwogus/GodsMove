package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
class CommunityCommentRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val repository: CommunityCommentRepository
) {
    @Test
    fun `find root page orders latest first and applies cursor`() {
        val fixture = persistPostFixture()
        val older = persistComment(fixture.post, fixture.member, "older", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        val middle = persistComment(fixture.post, fixture.member, "middle", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
        val newest = persistComment(fixture.post, fixture.member, "newest", createdAt = LocalDateTime.of(2026, 6, 12, 11, 0))
        persistComment(
            fixture.post,
            fixture.member,
            "reply",
            parent = newest,
            createdAt = LocalDateTime.of(2026, 6, 12, 11, 1)
        )
        entityManager.flush()
        entityManager.clear()

        val firstPage = repository.findRootPage(
            postId = requireNotNull(fixture.post.id),
            cursorCreatedAt = null,
            cursorId = null,
            pageable = PageRequest.of(0, 2)
        )
        val secondPage = repository.findRootPage(
            postId = requireNotNull(fixture.post.id),
            cursorCreatedAt = firstPage.last().createdAt,
            cursorId = requireNotNull(firstPage.last().id),
            pageable = PageRequest.of(0, 2)
        )

        assertThat(firstPage.map { it.body }).containsExactly("newest", "middle")
        assertThat(firstPage).allMatch { it.parentComment == null }
        assertThat(secondPage.map { it.body }).containsExactly("older")
        assertThat(older.id).isNotNull()
        assertThat(middle.id).isNotNull()
    }

    @Test
    fun `find replies returns only requested parents oldest first`() {
        val fixture = persistPostFixture()
        val root1 = persistComment(fixture.post, fixture.member, "root1", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        val root2 = persistComment(fixture.post, fixture.member, "root2", createdAt = LocalDateTime.of(2026, 6, 12, 9, 1))
        persistComment(fixture.post, fixture.member, "root1 reply 2", parent = root1, createdAt = LocalDateTime.of(2026, 6, 12, 9, 3))
        persistComment(fixture.post, fixture.member, "root1 reply 1", parent = root1, createdAt = LocalDateTime.of(2026, 6, 12, 9, 2))
        persistComment(fixture.post, fixture.member, "root2 reply", parent = root2, createdAt = LocalDateTime.of(2026, 6, 12, 9, 4))
        entityManager.flush()
        entityManager.clear()

        val replies = repository.findRepliesByParentIds(listOf(requireNotNull(root1.id)))

        assertThat(replies.map { it.body }).containsExactly("root1 reply 1", "root1 reply 2")
        assertThat(replies).allMatch { it.parentComment?.id == root1.id }
        assertThat(root2.id).isNotNull()
    }

    private fun persistPostFixture(): Fixture {
        val createdAt = LocalDateTime.of(2026, 6, 12, 8, 0)
        val member = persist(Member(email = "member-${UUID.randomUUID()}@example.com", passwordHash = null), createdAt)
        val crop = persist(
            Crop(
                externalNo = 422,
                name = "황기",
                usePartCategory = CropUsePartCategory.ROOT_BARK
            ),
            createdAt
        )
        val post = persist(
            CommunityPost(
                author = member,
                crop = crop,
                postType = CommunityPostType.QUESTION,
                title = "황기 발아율",
                body = "싹이 거의 올라오지 않아요."
            ),
            createdAt
        )
        return Fixture(member, post)
    }

    private fun persistComment(
        post: CommunityPost,
        member: Member,
        body: String,
        parent: CommunityComment? = null,
        createdAt: LocalDateTime
    ): CommunityComment =
        persist(
            CommunityComment(
                post = post,
                parentComment = parent,
                author = member,
                body = body
            ),
            createdAt
        )

    private fun <T : BaseTimeEntity> persist(entity: T, createdAt: LocalDateTime): T {
        setTimestamps(entity, createdAt)
        return entityManager.persistAndFlush(entity)
    }

    private fun setTimestamps(entity: BaseTimeEntity, createdAt: LocalDateTime) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
        BaseTimeEntity::class.java.getDeclaredField("updatedAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
    }

    private data class Fixture(val member: Member, val post: CommunityPost)
}
