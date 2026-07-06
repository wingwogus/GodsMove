package com.chamchamcham.domain.community

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(CommunityPostQueryRepositoryImpl::class)
class CommunityPostQueryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val queryRepository: CommunityPostQueryRepository
) {
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var hwanggiCrop: Crop
    private lateinit var ginsengCrop: Crop

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val hwanggiCropId: UUID
        get() = requireNotNull(hwanggiCrop.id) { "Persisted crop id is required" }

    private val now = LocalDateTime.of(2026, 6, 12, 7, 0)

    @BeforeEach
    fun setUp() {
        member = persist(Member(email = "member@example.com", passwordHash = null), now)
        otherMember = persist(Member(email = "other@example.com", passwordHash = null), now)
        hwanggiCrop = persist(crop(name = "황기", externalNo = 422), now)
        ginsengCrop = persist(crop(name = "인삼", externalNo = 107), now)
    }

    @Test
    fun `search returns active posts latest first with cursor`() {
        persistPost(title = "최신 글", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0))
        persistPost(title = "중간 글", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        persistPost(title = "오래된 글", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0))
        persistPost(title = "삭제된 글", createdAt = LocalDateTime.of(2026, 6, 12, 11, 0), isDeleted = true)
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.search(condition(size = 2))
        val secondPage = queryRepository.search(
            condition(
                size = 2,
                cursorCreatedAt = firstPage.rows.last().post.createdAt,
                cursorId = requireNotNull(firstPage.rows.last().post.id)
            )
        )

        assertThat(firstPage.rows.map { it.post.title }).containsExactly("최신 글", "중간 글")
        assertThat(firstPage.rows.map { it.post.title }).doesNotContain("삭제된 글")
        assertThat(secondPage.rows.map { it.post.title }).containsExactly("오래된 글")
    }

    @Test
    fun `search filters by crop post type and keyword`() {
        persistPost(title = "황기 발아율 질문", body = "발아가 안 됩니다", crop = hwanggiCrop, postType = CommunityPostType.QUESTION)
        persistPost(title = "인삼 발아율 질문", body = "발아가 안 됩니다", crop = ginsengCrop, postType = CommunityPostType.QUESTION)
        persistPost(title = "황기 자유글", body = "발아 얘기", crop = hwanggiCrop, postType = CommunityPostType.GENERAL)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(
            condition(
                cropId = hwanggiCropId,
                postType = CommunityPostType.QUESTION,
                keyword = "발아"
            )
        )

        assertThat(result.rows.map { it.post.title }).containsExactly("황기 발아율 질문")
        assertThat(result.rows.all { it.post.crop.id == hwanggiCropId }).isTrue()
        assertThat(result.rows.all { it.post.postType == CommunityPostType.QUESTION }).isTrue()
    }

    @Test
    fun `search filters likedOnly and mineOnly`() {
        val likedPost = persistPost(title = "내가 좋아요한 글", author = otherMember)
        persistPost(title = "내 글", author = member)
        persist(CommunityPostLike(post = likedPost, member = member), LocalDateTime.of(2026, 6, 12, 10, 10))
        entityManager.flush()
        entityManager.clear()

        val liked = queryRepository.search(condition(likedOnly = true))
        val mine = queryRepository.search(condition(mineOnly = true))

        assertThat(liked.rows.map { it.post.title }).containsExactly("내가 좋아요한 글")
        assertThat(liked.rows.first().likedByMe).isTrue()
        assertThat(mine.rows.map { it.post.author.id }).containsOnly(memberId)
    }

    private fun persistPost(
        title: String,
        body: String = "본문",
        author: Member = otherMember,
        crop: Crop = hwanggiCrop,
        postType: CommunityPostType = CommunityPostType.GENERAL,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 6, 12, 9, 0),
        isDeleted: Boolean = false
    ): CommunityPost {
        val post = persist(
            CommunityPost(
                author = author,
                crop = crop,
                postType = postType,
                title = title,
                body = body,
                isDeleted = isDeleted
            ),
            createdAt
        )
        persistComment(post)
        persistLike(post)
        persistThumbnail(post)
        return post
    }

    private fun persistComment(post: CommunityPost) {
        persist(
            CommunityComment(
                post = post,
                author = member,
                body = "댓글"
            ),
            LocalDateTime.of(2026, 6, 12, 9, 30)
        )
    }

    private fun persistLike(post: CommunityPost) {
        persist(CommunityPostLike(post = post, member = otherMember), LocalDateTime.of(2026, 6, 12, 9, 40))
    }

    private fun persistThumbnail(post: CommunityPost) {
        val media = persist(
            UploadedMedia(
                owner = otherMember,
                mediaType = UploadedMediaType.IMAGE,
                usageType = UploadedMediaUsageType.COMMUNITY_POST,
                fileUrl = "https://example.test/${post.title}.jpg",
                cloudinaryPublicId = "community/${post.title}",
                status = UploadedMediaStatus.ATTACHED
            ),
            LocalDateTime.of(2026, 6, 12, 9, 50)
        )
        persist(CommunityPostMedia(post = post, uploadedMedia = media, displayOrder = 0), LocalDateTime.of(2026, 6, 12, 9, 51))
    }

    private fun condition(
        cropId: UUID? = null,
        postType: CommunityPostType? = null,
        keyword: String? = null,
        likedOnly: Boolean = false,
        mineOnly: Boolean = false,
        cursorCreatedAt: LocalDateTime? = null,
        cursorId: UUID? = null,
        size: Int = 20
    ): CommunityPostQueryRepository.SearchCondition =
        CommunityPostQueryRepository.SearchCondition(
            memberId = memberId,
            cropId = cropId,
            postType = postType,
            keyword = keyword,
            likedOnly = likedOnly,
            mineOnly = mineOnly,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            size = size
        )

    private fun crop(name: String, externalNo: Int): Crop =
        Crop(externalNo = externalNo, name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)

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
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class CommunityPostQueryRepositoryTestApplication
