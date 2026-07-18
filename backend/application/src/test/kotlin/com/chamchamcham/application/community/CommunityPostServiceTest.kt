package com.chamchamcham.application.community

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.community.CommunityCommentRepository
import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostLike
import com.chamchamcham.domain.community.CommunityPostLikeRepository
import com.chamchamcham.domain.community.CommunityPostMedia
import com.chamchamcham.domain.community.CommunityPostMediaRepository
import com.chamchamcham.domain.community.CommunityPostQueryRepository
import com.chamchamcham.domain.community.CommunityPostRepository
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CommunityPostServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val secondPostId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondCropId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val mediaId1 = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val mediaId2 = UUID.fromString("00000000-0000-0000-0000-000000000402")
    private val replacementMediaId = UUID.fromString("00000000-0000-0000-0000-000000000403")
    private val postCreatedAt = LocalDateTime.of(2026, 6, 12, 9, 0)
    private val cursorCodec = OpaqueCursorCodec()

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository
    @Mock private lateinit var communityPostMediaRepository: CommunityPostMediaRepository
    @Mock private lateinit var communityCommentRepository: CommunityCommentRepository
    @Mock private lateinit var communityPostLikeRepository: CommunityPostLikeRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var communityPostQueryRepository: CommunityPostQueryRepository

    private lateinit var service: CommunityPostService
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var crop: Crop
    private lateinit var secondCrop: Crop
    private lateinit var record: FarmingRecord
    private lateinit var media1: UploadedMedia
    private lateinit var media2: UploadedMedia
    private lateinit var replacementMedia: UploadedMedia
    private lateinit var existingPost: CommunityPost
    private lateinit var existingLike: CommunityPostLike

    @BeforeEach
    fun setUp() {
        member = member(memberId).apply { nickname = "황기농부" }
        otherMember = member(otherMemberId)
        crop = crop(cropId, "황기")
        secondCrop = crop(secondCropId, "인삼")
        record = farmingRecord(member, crop)
        media1 = uploadedMedia(member, mediaId1)
        media2 = uploadedMedia(member, mediaId2)
        replacementMedia = uploadedMedia(member, replacementMediaId)
        existingPost = existingPost(member, crop)
        existingLike = CommunityPostLike(id = UUID.randomUUID(), post = existingPost, member = member)

        service = CommunityPostService(
            memberRepository = memberRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository,
            uploadedMediaRepository = uploadedMediaRepository,
            communityPostRepository = communityPostRepository,
            communityPostMediaRepository = communityPostMediaRepository,
            communityCommentRepository = communityCommentRepository,
            communityPostLikeRepository = communityPostLikeRepository,
            memberCropRepository = memberCropRepository,
            communityPostQueryRepository = communityPostQueryRepository,
            cursorCodec = cursorCodec
        )
    }

    @Test
    fun `create stores post with crop farming record and attached images`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(record)
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1, mediaId2))).thenReturn(listOf(media1, media2))
        `when`(communityPostRepository.save(any(CommunityPost::class.java))).thenAnswer { invocation ->
            val post = invocation.arguments[0] as CommunityPost
            CommunityPost(
                id = postId,
                author = post.author,
                crop = post.crop,
                farmingRecord = post.farmingRecord,
                postType = post.postType,
                title = post.title,
                body = post.body
            )
        }

        val result = service.create(createCommand(mediaIds = listOf(mediaId1, mediaId2)))

        assertEquals(postId, result.id)
        val savedPost = capturedPost()
        assertEquals(memberId, savedPost.author.id)
        assertEquals(cropId, savedPost.crop.id)
        assertEquals(recordId, savedPost.farmingRecord?.id)
        assertEquals(CommunityPostType.QUESTION, savedPost.postType)
        assertEquals("황기 발아율이 낮아요", savedPost.title)
        assertEquals("싹이 거의 올라오지 않아요.", savedPost.body)
        assertEquals(UploadedMediaStatus.ATTACHED, media1.status)
        assertEquals(UploadedMediaStatus.ATTACHED, media2.status)
        assertThat(capturedPostMedia().map { it.displayOrder }).containsExactly(0, 1)
    }

    @Test
    fun `create rejects farming record with different crop`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId))
            .thenReturn(farmingRecord(member, secondCrop))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(farmingRecordId = recordId))
        }

        assertEquals(ErrorCode.COMMUNITY_FARMING_RECORD_CROP_MISMATCH, exception.errorCode)
        verify(communityPostRepository, never()).save(any(CommunityPost::class.java))
    }

    @Test
    fun `update allows author to change crop farming record content and images`() {
        val secondRecord = farmingRecord(member, secondCrop)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(secondRecord)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
        `when`(uploadedMediaRepository.findAllById(listOf(replacementMediaId))).thenReturn(listOf(replacementMedia))
        `when`(communityPostMediaRepository.findByUploadedMediaIdIn(listOf(replacementMediaId))).thenReturn(emptyList())

        val result = service.update(updateCommand())

        assertEquals(postId, result.id)
        assertEquals(secondCropId, existingPost.crop.id)
        assertEquals(CommunityPostType.GENERAL, existingPost.postType)
        assertEquals("수정된 제목", existingPost.title)
        assertEquals("수정된 본문", existingPost.body)
        assertEquals(recordId, existingPost.farmingRecord?.id)
        assertEquals(UploadedMediaStatus.ATTACHED, replacementMedia.status)
    }

    @Test
    fun `update keeps existing post image and attaches new image in requested order`() {
        val secondRecord = farmingRecord(member, secondCrop)
        val existingMedia = uploadedMedia(member, mediaId1, status = UploadedMediaStatus.ATTACHED)
        val newMedia = uploadedMedia(member, replacementMediaId)
        val existingPostMedia = CommunityPostMedia(post = existingPost, uploadedMedia = existingMedia, displayOrder = 0)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(secondRecord)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(listOf(existingPostMedia))
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1, replacementMediaId))).thenReturn(
            listOf(existingMedia, newMedia)
        )
        `when`(communityPostMediaRepository.findByUploadedMediaIdIn(listOf(mediaId1, replacementMediaId))).thenReturn(
            listOf(existingPostMedia)
        )

        service.update(updateCommand(mediaIds = listOf(mediaId1, replacementMediaId)))

        assertEquals(UploadedMediaStatus.ATTACHED, existingMedia.status)
        assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
        val postMedia = capturedPostMedia()
        assertThat(postMedia.map { it.uploadedMedia.id }).containsExactly(mediaId1, replacementMediaId)
        assertThat(postMedia.map { it.displayOrder }).containsExactly(0, 1)
    }

    @Test
    fun `update marks removed existing image as deleted`() {
        val secondRecord = farmingRecord(member, secondCrop)
        val keptMedia = uploadedMedia(member, mediaId1, status = UploadedMediaStatus.ATTACHED)
        val removedMedia = uploadedMedia(member, mediaId2, status = UploadedMediaStatus.ATTACHED)
        val keptPostMedia = CommunityPostMedia(post = existingPost, uploadedMedia = keptMedia, displayOrder = 0)
        val removedPostMedia = CommunityPostMedia(post = existingPost, uploadedMedia = removedMedia, displayOrder = 1)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(secondRecord)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(
            listOf(keptPostMedia, removedPostMedia)
        )
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(keptMedia))
        `when`(communityPostMediaRepository.findByUploadedMediaIdIn(listOf(mediaId1))).thenReturn(listOf(keptPostMedia))

        service.update(updateCommand(mediaIds = listOf(mediaId1)))

        assertEquals(UploadedMediaStatus.ATTACHED, keptMedia.status)
        assertEquals(UploadedMediaStatus.DELETED, removedMedia.status)
        assertThat(capturedPostMedia().map { it.uploadedMedia.id }).containsExactly(mediaId1)
    }

    @Test
    fun `update rejects image already attached to another post`() {
        val secondRecord = farmingRecord(member, secondCrop)
        val otherPost = existingPost(member, crop, secondPostId)
        val otherPostMedia = uploadedMedia(member, mediaId1, status = UploadedMediaStatus.ATTACHED)
        val otherPostMediaRow = CommunityPostMedia(post = otherPost, uploadedMedia = otherPostMedia, displayOrder = 0)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(secondRecord)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(otherPostMedia))
        `when`(communityPostMediaRepository.findByUploadedMediaIdIn(listOf(mediaId1))).thenReturn(listOf(otherPostMediaRow))

        val exception = assertThrows(BusinessException::class.java) {
            service.update(updateCommand(mediaIds = listOf(mediaId1)))
        }

        assertEquals(ErrorCode.MEDIA_NOT_ATTACHABLE, exception.errorCode)
        verify(communityPostMediaRepository, never()).deleteByPost(existingPost)
    }

    @Test
    fun `update rejects another member image`() {
        val secondRecord = farmingRecord(member, secondCrop)
        val otherMemberMedia = uploadedMedia(otherMember, mediaId1)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(cropRepository.findById(secondCropId)).thenReturn(Optional.of(secondCrop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(secondRecord)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(otherMemberMedia))
        `when`(communityPostMediaRepository.findByUploadedMediaIdIn(listOf(mediaId1))).thenReturn(emptyList())

        val exception = assertThrows(BusinessException::class.java) {
            service.update(updateCommand(mediaIds = listOf(mediaId1)))
        }

        assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
        verify(communityPostMediaRepository, never()).deleteByPost(existingPost)
    }

    @Test
    fun `delete rejects non author`() {
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)

        val exception = assertThrows(BusinessException::class.java) {
            service.delete(CommunityPostCommand.Delete(memberId = otherMemberId, postId = postId))
        }

        assertEquals(ErrorCode.COMMUNITY_FORBIDDEN, exception.errorCode)
        assertFalse(existingPost.isDeleted)
    }

    @Test
    fun `toggle like creates then removes like row`() {
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(communityPostLikeRepository.findByPostIdAndMemberId(postId, memberId))
            .thenReturn(null)
            .thenReturn(existingLike)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(communityPostLikeRepository.countByPostId(postId)).thenReturn(1L).thenReturn(0L)

        val first = service.toggleLike(CommunityPostCommand.ToggleLike(memberId = memberId, postId = postId))
        val second = service.toggleLike(CommunityPostCommand.ToggleLike(memberId = memberId, postId = postId))

        assertTrue(first.liked)
        assertEquals(1, first.likeCount)
        verify(communityPostLikeRepository).save(any(CommunityPostLike::class.java))
        assertFalse(second.liked)
        assertEquals(0, second.likeCount)
        verify(communityPostLikeRepository).delete(existingLike)
    }

    @Test
    fun `list boards de duplicates member crops by crop id preserving first encounter order`() {
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(
            listOf(memberCrop(member, crop), memberCrop(member, crop), memberCrop(member, secondCrop))
        )

        val boards = service.listBoards(memberId)

        assertThat(boards.map { it.cropId }).containsExactly(cropId, secondCropId)
        assertThat(boards.map { it.cropName }).containsExactly("황기", "인삼")
    }

    @Test
    fun `list post crops maps distinct crops from the query repository`() {
        `when`(communityPostQueryRepository.findDistinctCropsByAuthor(memberId))
            .thenReturn(listOf(crop, secondCrop))

        val boards = service.listPostCrops(memberId)

        assertThat(boards.map { it.cropId }).containsExactly(cropId, secondCropId)
        assertThat(boards.map { it.cropName }).containsExactly("황기", "인삼")
    }

    @Test
    fun `get detail returns ordered images counts and liked by me`() {
        setCreatedAt(existingPost, postCreatedAt)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(
            listOf(
                CommunityPostMedia(post = existingPost, uploadedMedia = media1, displayOrder = 0),
                CommunityPostMedia(post = existingPost, uploadedMedia = media2, displayOrder = 1)
            )
        )
        `when`(communityCommentRepository.countByPostIdAndIsDeletedFalse(postId)).thenReturn(3L)
        `when`(communityPostLikeRepository.countByPostId(postId)).thenReturn(8L)
        `when`(communityPostLikeRepository.existsByPostIdAndMemberId(postId, memberId)).thenReturn(true)

        val detail = service.getDetail(memberId = memberId, postId = postId)

        assertEquals(postId, detail.id)
        assertThat(detail.imageUrls).containsExactly("https://example.test/1.jpg", "https://example.test/2.jpg")
        assertEquals(3, detail.commentCount)
        assertEquals(8, detail.likeCount)
        assertTrue(detail.likedByMe)
    }

    @Test
    fun `guest detail returns false without like lookup`() {
        setCreatedAt(existingPost, postCreatedAt)
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(existingPost)
        `when`(communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)).thenReturn(emptyList())
        `when`(communityCommentRepository.countByPostIdAndIsDeletedFalse(postId)).thenReturn(0)
        `when`(communityPostLikeRepository.countByPostId(postId)).thenReturn(0)

        val detail = service.getDetail(memberId = null, postId = postId)

        assertFalse(detail.likedByMe)
        verify(communityPostLikeRepository, never()).existsByPostIdAndMemberId(postId, memberId)
    }

    @Test
    fun `search maps application condition to query repository and next cursor`() {
        val requestedSize = 1
        val overflowPost = existingPost(member, crop, secondPostId).also {
            setCreatedAt(it, postCreatedAt.minusMinutes(1))
        }
        setCreatedAt(existingPost, postCreatedAt)
        `when`(
            communityPostQueryRepository.search(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = memberId,
                    authorMemberId = otherMemberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = null,
                    size = requestedSize + 1
                )
            )
        ).thenReturn(
            CommunityPostQueryRepository.SearchResult(
                rows = listOf(
                    queryRow(existingPost, thumbnailUrl = "https://example.test/1.jpg"),
                    queryRow(overflowPost, thumbnailUrl = "https://example.test/2.jpg")
                )
            )
        )

        val page = service.search(
            CommunityPostSearchCondition(
                memberId = memberId,
                authorMemberId = otherMemberId,
                cropIds = listOf(cropId),
                postType = CommunityPostType.QUESTION,
                keyword = "발아",
                likedOnly = false,
                mineOnly = false,
                sort = CommunityPostSort.LATEST,
                cursor = null,
                size = requestedSize
            )
        )

        assertThat(page.items).hasSize(1)
        assertEquals(postId, page.items.single().id)
        assertThat(page.nextCursor).isNotBlank()
        val nextCursor = cursorCodec.decode(page.nextCursor!!, CommunityPostCursorPayload::class.java)
        assertEquals(CommunityPostSort.LATEST, nextCursor.sort)
        assertEquals(null, nextCursor.score)
        assertEquals(postCreatedAt, nextCursor.createdAt)
        assertEquals(postId, nextCursor.id)
    }

    @Test
    fun `guest search delegates without viewer personalization`() {
        `when`(
            communityPostQueryRepository.search(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = null,
                    authorMemberId = otherMemberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(CommunityPostQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(memberId = null, authorMemberId = otherMemberId))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `guest search rejects liked only before repository access`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(memberId = null, likedOnly = true))
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `guest search rejects mine only before repository access`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(memberId = null, mineOnly = true))
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search encodes count based next cursor with score`() {
        val requestedSize = 1
        val overflowPost = existingPost(member, crop, secondPostId).also {
            setCreatedAt(it, postCreatedAt.minusMinutes(1))
        }
        setCreatedAt(existingPost, postCreatedAt)
        `when`(
            communityPostQueryRepository.search(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LIKE,
                    cursor = null,
                    size = requestedSize + 1
                )
            )
        ).thenReturn(
            CommunityPostQueryRepository.SearchResult(
                rows = listOf(
                    queryRow(existingPost, thumbnailUrl = "https://example.test/1.jpg", score = 8),
                    queryRow(overflowPost, thumbnailUrl = "https://example.test/2.jpg", score = 7)
                )
            )
        )

        val page = service.search(searchCondition(sort = CommunityPostSort.LIKE, size = requestedSize))

        assertThat(page.items).hasSize(1)
        val nextCursor = cursorCodec.decode(page.nextCursor!!, CommunityPostCursorPayload::class.java)
        assertEquals(CommunityPostSort.LIKE, nextCursor.sort)
        assertEquals(8L, nextCursor.score)
        assertEquals(postCreatedAt, nextCursor.createdAt)
        assertEquals(postId, nextCursor.id)
    }

    @Test
    fun `search fails when count based next cursor row has null score`() {
        val requestedSize = 1
        val overflowPost = existingPost(member, crop, secondPostId).also {
            setCreatedAt(it, postCreatedAt.minusMinutes(1))
        }
        setCreatedAt(existingPost, postCreatedAt)
        `when`(
            communityPostQueryRepository.search(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LIKE,
                    cursor = null,
                    size = requestedSize + 1
                )
            )
        ).thenReturn(
            CommunityPostQueryRepository.SearchResult(
                rows = listOf(
                    queryRow(existingPost, thumbnailUrl = "https://example.test/1.jpg", score = null),
                    queryRow(overflowPost, thumbnailUrl = "https://example.test/2.jpg", score = 7)
                )
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.search(searchCondition(sort = CommunityPostSort.LIKE, size = requestedSize))
        }
    }

    @Test
    fun `search decodes valid incoming cursor for query repository`() {
        val cursor = cursorCodec.encode(
            CommunityPostCursorPayload(
                sort = CommunityPostSort.LIKE,
                score = 8,
                createdAt = postCreatedAt,
                id = postId
            )
        )
        val decodedCursor = CommunityPostQueryRepository.Cursor(
            sort = CommunityPostSort.LIKE,
            score = 8,
            createdAt = postCreatedAt,
            id = postId
        )
        `when`(
            communityPostQueryRepository.search(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LIKE,
                    cursor = decodedCursor,
                    size = 21
                )
            )
        ).thenReturn(CommunityPostQueryRepository.SearchResult(rows = emptyList()))

        val page = service.search(searchCondition(sort = CommunityPostSort.LIKE, cursor = cursor))

        assertThat(page.items).isEmpty()
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `search rejects zero size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = 0))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search rejects negative size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = -1))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search rejects max integer size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = Int.MAX_VALUE))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search rejects cursor when embedded sort differs from request sort`() {
        val cursor = cursorCodec.encode(
            CommunityPostCursorPayload(
                sort = CommunityPostSort.LIKE,
                score = 8,
                createdAt = postCreatedAt,
                id = postId
            )
        )

        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(sort = CommunityPostSort.LATEST, cursor = cursor))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search rejects count based sort cursor with null score`() {
        val cursor = cursorCodec.encode(
            CommunityPostCursorPayload(
                sort = CommunityPostSort.LIKE,
                score = null,
                createdAt = postCreatedAt,
                id = postId
            )
        )

        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(sort = CommunityPostSort.LIKE, cursor = cursor))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `search rejects malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(cursor = "not-a-valid-cursor"))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
        verifyNoInteractions(communityPostQueryRepository)
    }

    @Test
    fun `count delegates to query repository with condition and no cursor`() {
        `when`(
            communityPostQueryRepository.count(
                CommunityPostQueryRepository.SearchCondition(
                    memberId = memberId,
                    authorMemberId = otherMemberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "발아",
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = null,
                    size = 1
                )
            )
        ).thenReturn(5L)

        val total = service.count(searchCondition(authorMemberId = otherMemberId))

        assertEquals(5L, total)
        verifyNoInteractions(communityPostRepository)
    }

    private fun member(id: UUID): Member =
        Member(id = id, email = "$id@example.com", passwordHash = null)

    private fun crop(id: UUID, name: String): Crop =
        Crop(id = id, externalNo = id.hashCode(), name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)

    private fun memberCrop(member: Member, crop: Crop): MemberCrop =
        MemberCrop(member = member, farm = farm(member), crop = crop)

    private fun farmingRecord(member: Member, crop: Crop): FarmingRecord =
        FarmingRecord(
            id = recordId,
            member = member,
            farm = farm(member),
            crop = crop,
            workType = WorkType.PLANTING,
            workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            entryMode = EntryMode.MANUAL
        )

    private fun uploadedMedia(
        member: Member,
        id: UUID,
        usageType: UploadedMediaUsageType = UploadedMediaUsageType.COMMUNITY_POST,
        status: UploadedMediaStatus = UploadedMediaStatus.TEMP
    ): UploadedMedia {
        val index = if (id == mediaId2) "2" else "1"
        return UploadedMedia(
            id = id,
            owner = member,
            mediaType = UploadedMediaType.IMAGE,
            usageType = usageType,
            fileUrl = "https://example.test/$index.jpg",
            cloudinaryPublicId = "community/$index",
            status = status
        )
    }

    private fun existingPost(author: Member, crop: Crop, id: UUID = postId): CommunityPost =
        CommunityPost(
            id = id,
            author = author,
            crop = crop,
            farmingRecord = record,
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율이 낮아요",
            body = "싹이 거의 올라오지 않아요."
        )

    private fun searchCondition(
        memberId: UUID? = this.memberId,
        authorMemberId: UUID? = null,
        likedOnly: Boolean = false,
        mineOnly: Boolean = false,
        sort: CommunityPostSort = CommunityPostSort.LATEST,
        cursor: String? = null,
        size: Int = 20
    ): CommunityPostSearchCondition =
        CommunityPostSearchCondition(
            memberId = memberId,
            authorMemberId = authorMemberId,
            cropIds = listOf(cropId),
            postType = CommunityPostType.QUESTION,
            keyword = "발아",
            likedOnly = likedOnly,
            mineOnly = mineOnly,
            sort = sort,
            cursor = cursor,
            size = size
        )

    private fun createCommand(
        mediaIds: List<UUID> = emptyList(),
        farmingRecordId: UUID? = recordId
    ): CommunityPostCommand.Create =
        CommunityPostCommand.Create(
            memberId = memberId,
            cropId = cropId,
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율이 낮아요",
            body = "싹이 거의 올라오지 않아요.",
            farmingRecordId = farmingRecordId,
            mediaIds = mediaIds
        )

    private fun updateCommand(
        mediaIds: List<UUID> = listOf(replacementMediaId),
        farmingRecordId: UUID? = recordId
    ): CommunityPostCommand.Update =
        CommunityPostCommand.Update(
            memberId = memberId,
            postId = postId,
            cropId = secondCropId,
            postType = CommunityPostType.GENERAL,
            title = "수정된 제목",
            body = "수정된 본문",
            farmingRecordId = farmingRecordId,
            mediaIds = mediaIds
        )

    private fun queryRow(
        post: CommunityPost,
        thumbnailUrl: String? = null,
        commentCount: Long = 3,
        likeCount: Long = 8,
        likedByMe: Boolean = true,
        score: Long? = null
    ): CommunityPostQueryRepository.Row =
        CommunityPostQueryRepository.Row(
            post = post,
            thumbnailUrl = thumbnailUrl,
            commentCount = commentCount,
            likeCount = likeCount,
            likedByMe = likedByMe,
            score = score
        )

    private fun capturedPost(): CommunityPost {
        val captor = ArgumentCaptor.forClass(CommunityPost::class.java)
        verify(communityPostRepository).save(captor.capture())
        return captor.value
    }

    private fun capturedPostMedia(): List<CommunityPostMedia> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<CommunityPostMedia>>
        verify(communityPostMediaRepository).saveAll(captor.capture())
        return captor.value.toList()
    }

    private fun farm(member: Member): Farm =
        Farm(owner = member, name = "약초농장", roadAddress = "서울시 강남구")

    private fun setCreatedAt(entity: BaseTimeEntity, createdAt: LocalDateTime) {
        val field = BaseTimeEntity::class.java.getDeclaredField("createdAt")
        field.isAccessible = true
        field.set(entity, createdAt)
    }
}
