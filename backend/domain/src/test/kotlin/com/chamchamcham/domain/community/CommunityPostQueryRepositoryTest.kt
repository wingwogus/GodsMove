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
    private val ginsengCropId: UUID
        get() = requireNotNull(ginsengCrop.id) { "Persisted crop id is required" }

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
                cursor = CommunityPostQueryRepository.Cursor(
                    sort = CommunityPostSort.LATEST,
                    score = null,
                    createdAt = firstPage.rows.last().post.createdAt,
                    id = requireNotNull(firstPage.rows.last().post.id)
                )
            )
        )

        assertThat(firstPage.rows.map { it.post.title }).containsExactly("최신 글", "중간 글")
        assertThat(firstPage.rows.map { it.post.title }).doesNotContain("삭제된 글")
        assertThat(secondPage.rows.map { it.post.title }).containsExactly("오래된 글")
    }

    @Test
    fun `search filters by multiple crops post type and keyword`() {
        persistPost(title = "황기 발아율 질문", body = "발아가 안 됩니다", crop = hwanggiCrop, postType = CommunityPostType.QUESTION)
        persistPost(title = "인삼 발아율 질문", body = "발아가 안 됩니다", crop = ginsengCrop, postType = CommunityPostType.QUESTION)
        persistPost(title = "황기 자유글", body = "발아 얘기", crop = hwanggiCrop, postType = CommunityPostType.GENERAL)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(
            condition(
                cropIds = listOf(hwanggiCropId, ginsengCropId),
                postType = CommunityPostType.QUESTION,
                keyword = "발아"
            )
        )

        assertThat(result.rows.map { it.post.title })
            .containsExactlyInAnyOrder("황기 발아율 질문", "인삼 발아율 질문")
        assertThat(result.rows.all { it.post.crop.id in listOf(hwanggiCropId, ginsengCropId) }).isTrue()
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

    @Test
    fun `search filters by author member id without changing viewer like state`() {
        val targetPost = persistPost(title = "다른 회원 글", author = otherMember)
        persistPost(title = "내 글", author = member)
        persist(CommunityPostLike(post = targetPost, member = member), LocalDateTime.of(2026, 6, 12, 10, 10))
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(
            condition(authorMemberId = requireNotNull(otherMember.id))
        )

        assertThat(result.rows.map { it.post.title }).containsExactly("다른 회원 글")
        assertThat(result.rows.single().likedByMe).isTrue()
    }

    @Test
    fun `search returns posts for guest with likedByMe false`() {
        persistPost(title = "비회원 공개 글", author = otherMember)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(memberId = null))

        assertThat(result.rows.map { it.post.title }).containsExactly("비회원 공개 글")
        assertThat(result.rows.single().likedByMe).isFalse()
    }

    @Test
    fun `search sorts by like count then latest`() {
        persistPost(title = "좋아요 둘 오래된 글", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0), likeCount = 2)
        persistPost(title = "좋아요 하나 글", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0), likeCount = 1)
        persistPost(title = "좋아요 둘 최신 글", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0), likeCount = 2)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(sort = CommunityPostSort.LIKE))

        assertThat(result.rows.map { it.post.title }).containsExactly("좋아요 둘 최신 글", "좋아요 둘 오래된 글", "좋아요 하나 글")
        assertThat(result.rows.map { it.score }).containsExactly(2L, 2L, 1L)
        assertThat(result.rows.map { it.likeCount }).containsExactly(2L, 2L, 1L)
    }

    @Test
    fun `search sorts by non-deleted comment count then latest`() {
        persistPost(
            title = "댓글 둘 오래된 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 8, 0),
            commentCount = 2
        )
        persistPost(
            title = "댓글 하나와 삭제 댓글 많은 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 10, 0),
            commentCount = 1,
            deletedCommentCount = 3
        )
        persistPost(
            title = "댓글 둘 최신 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
            commentCount = 2
        )
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(sort = CommunityPostSort.COMMENT))

        assertThat(result.rows.map { it.post.title }).containsExactly("댓글 둘 최신 글", "댓글 둘 오래된 글", "댓글 하나와 삭제 댓글 많은 글")
        assertThat(result.rows.map { it.score }).containsExactly(2L, 2L, 1L)
        assertThat(result.rows.map { it.commentCount }).containsExactly(2L, 2L, 1L)
    }

    @Test
    fun `search sorts by popular score then latest`() {
        persistPost(
            title = "인기 셋 오래된 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 8, 0),
            likeCount = 1,
            commentCount = 2
        )
        persistPost(
            title = "인기 둘 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 10, 0),
            likeCount = 1,
            commentCount = 1
        )
        persistPost(
            title = "인기 셋 최신 글",
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
            likeCount = 3
        )
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(sort = CommunityPostSort.POPULAR))

        assertThat(result.rows.map { it.post.title }).containsExactly("인기 셋 최신 글", "인기 셋 오래된 글", "인기 둘 글")
        assertThat(result.rows.map { it.score }).containsExactly(3L, 3L, 2L)
        assertThat(result.rows.map { it.likeCount + it.commentCount }).containsExactly(3L, 3L, 2L)
    }

    @Test
    fun `search uses count based cursor pagination`() {
        persistPost(title = "좋아요 다섯 글", createdAt = LocalDateTime.of(2026, 6, 12, 11, 0), likeCount = 5)
        persistPost(title = "좋아요 넷 최신 글", createdAt = LocalDateTime.of(2026, 6, 12, 10, 0), likeCount = 4)
        persistPost(title = "좋아요 넷 오래된 글", createdAt = LocalDateTime.of(2026, 6, 12, 9, 0), likeCount = 4)
        persistPost(title = "좋아요 셋 글", createdAt = LocalDateTime.of(2026, 6, 12, 8, 0), likeCount = 3)
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.search(condition(sort = CommunityPostSort.LIKE, size = 2))
        val cursorRow = firstPage.rows.last()
        val secondPage = queryRepository.search(
            condition(
                sort = CommunityPostSort.LIKE,
                size = 2,
                cursor = CommunityPostQueryRepository.Cursor(
                    sort = CommunityPostSort.LIKE,
                    score = cursorRow.score,
                    createdAt = cursorRow.post.createdAt,
                    id = requireNotNull(cursorRow.post.id)
                )
            )
        )

        assertThat(firstPage.rows.map { it.post.title }).containsExactly("좋아요 다섯 글", "좋아요 넷 최신 글")
        assertThat(secondPage.rows.map { it.post.title }).containsExactly("좋아요 넷 오래된 글", "좋아요 셋 글")
    }

    @Test
    fun `search condition supports sort and structured cursor`() {
        val cursor = CommunityPostQueryRepository.Cursor(
            sort = CommunityPostSort.LIKE,
            score = 3,
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0),
            id = UUID.fromString("00000000-0000-0000-0000-000000000101")
        )

        val condition = condition(sort = CommunityPostSort.LIKE, cursor = cursor)

        assertThat(condition.sort).isEqualTo(CommunityPostSort.LIKE)
        assertThat(condition.cursor).isEqualTo(cursor)
    }

    @Test
    fun `count returns total matching posts ignoring cursor and size`() {
        persistPost(title = "황기 발아율 질문", crop = hwanggiCrop, postType = CommunityPostType.QUESTION)
        persistPost(title = "황기 자유글", crop = hwanggiCrop, postType = CommunityPostType.GENERAL)
        persistPost(title = "인삼 발아율 질문", crop = ginsengCrop, postType = CommunityPostType.QUESTION)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(condition(cropId = hwanggiCropId, size = 1))

        assertThat(total).isEqualTo(2)
    }

    @Test
    fun `count applies keyword post type and mineOnly likedOnly filters same as search`() {
        val likedPost = persistPost(title = "황기 발아율 질문", body = "발아가 안 됩니다", crop = hwanggiCrop, postType = CommunityPostType.QUESTION, author = otherMember)
        persistPost(title = "인삼 발아율 질문", body = "발아가 안 됩니다", crop = ginsengCrop, postType = CommunityPostType.QUESTION)
        persist(CommunityPostLike(post = likedPost, member = member), LocalDateTime.of(2026, 6, 12, 10, 10))
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(condition(cropId = hwanggiCropId, postType = CommunityPostType.QUESTION, keyword = "발아"))
        val likedTotal = queryRepository.count(condition(likedOnly = true))

        assertThat(total).isEqualTo(1)
        assertThat(likedTotal).isEqualTo(1)
    }

    @Test
    fun `count excludes deleted posts`() {
        persistPost(title = "정상 글")
        persistPost(title = "삭제된 글", isDeleted = true)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(condition())

        assertThat(total).isEqualTo(1)
    }

    @Test
    fun `count filters by author member id`() {
        persistPost(title = "다른 회원 글", author = otherMember)
        persistPost(title = "내 글", author = member)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(
            condition(authorMemberId = requireNotNull(otherMember.id))
        )

        assertThat(total).isEqualTo(1)
    }

    @Test
    fun `count preserves author filter for guest`() {
        persistPost(title = "다른 회원 글", author = otherMember)
        persistPost(title = "내 글", author = member)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(
            condition(
                memberId = null,
                authorMemberId = requireNotNull(otherMember.id)
            )
        )

        assertThat(total).isEqualTo(1)
    }

    @Test
    fun `findDistinctCropsByAuthor dedupes crops and excludes deleted posts and other authors`() {
        persistPost(title = "황기 글 1", author = member, crop = hwanggiCrop)
        persistPost(title = "황기 글 2", author = member, crop = hwanggiCrop)
        persistPost(title = "삭제된 인삼 글", author = member, crop = ginsengCrop, isDeleted = true)
        persistPost(title = "다른 회원 인삼 글", author = otherMember, crop = ginsengCrop)
        entityManager.flush()
        entityManager.clear()

        val crops = queryRepository.findDistinctCropsByAuthor(memberId)

        assertThat(crops.map { it.id }).containsExactly(hwanggiCropId)
    }

    private fun persistPost(
        title: String,
        body: String = "본문",
        author: Member = otherMember,
        crop: Crop = hwanggiCrop,
        postType: CommunityPostType = CommunityPostType.GENERAL,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 6, 12, 9, 0),
        isDeleted: Boolean = false,
        commentCount: Int = 0,
        deletedCommentCount: Int = 0,
        likeCount: Int = 0
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
        repeat(commentCount) { persistComment(post) }
        repeat(deletedCommentCount) { persistComment(post, isDeleted = true) }
        repeat(likeCount) { persistLike(post, member = persistLikeMember()) }
        persistThumbnail(post)
        return post
    }

    private fun persistComment(post: CommunityPost, isDeleted: Boolean = false) {
        persist(
            CommunityComment(
                post = post,
                author = member,
                body = "댓글",
                isDeleted = isDeleted
            ),
            LocalDateTime.of(2026, 6, 12, 9, 30)
        )
    }

    private fun persistLike(post: CommunityPost, member: Member = otherMember) {
        persist(CommunityPostLike(post = post, member = member), LocalDateTime.of(2026, 6, 12, 9, 40))
    }

    private fun persistLikeMember(): Member {
        return persist(
            Member(email = "like-${UUID.randomUUID()}@example.com", passwordHash = null),
            now
        )
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
        memberId: UUID? = this.memberId,
        authorMemberId: UUID? = null,
        cropId: UUID? = null,
        cropIds: List<UUID> = listOfNotNull(cropId),
        postType: CommunityPostType? = null,
        keyword: String? = null,
        likedOnly: Boolean = false,
        mineOnly: Boolean = false,
        sort: CommunityPostSort = CommunityPostSort.LATEST,
        cursor: CommunityPostQueryRepository.Cursor? = null,
        size: Int = 20
    ): CommunityPostQueryRepository.SearchCondition =
        CommunityPostQueryRepository.SearchCondition(
            memberId = memberId,
            authorMemberId = authorMemberId,
            cropIds = cropIds,
            postType = postType,
            keyword = keyword,
            likedOnly = likedOnly,
            mineOnly = mineOnly,
            sort = sort,
            cursor = cursor,
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
