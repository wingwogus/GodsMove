package com.chamchamcham.application.community

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.community.CommunityComment
import com.chamchamcham.domain.community.CommunityCommentRepository
import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostRepository
import com.chamchamcham.domain.community.CommunityPostType
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.data.domain.PageRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CommunityCommentServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val rootCommentId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val replyCommentId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000601")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository
    @Mock private lateinit var communityCommentRepository: CommunityCommentRepository
    @Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository

    private val cursorCodec = OpaqueCursorCodec()

    private lateinit var service: CommunityCommentService
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var post: CommunityPost
    private lateinit var rootComment: CommunityComment
    private lateinit var replyComment: CommunityComment

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "farmer@example.com", passwordHash = null)
        otherMember = Member(id = otherMemberId, email = "other@example.com", passwordHash = null)
        post = CommunityPost(
            id = postId,
            author = member,
            crop = Crop(
                id = UUID.fromString("00000000-0000-0000-0000-000000000301"),
                externalNo = 422,
                name = "황기",
                usePartCategory = CropUsePartCategory.ROOT_BARK
            ),
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율이 낮아요",
            body = "싹이 거의 올라오지 않아요."
        )
        rootComment = comment(id = rootCommentId, parent = null, author = member, body = "저도 궁금해요")
        replyComment = comment(id = replyCommentId, parent = rootComment, author = otherMember, body = "스크래치 작업을 해보세요")

        service = CommunityCommentService(
            memberRepository = memberRepository,
            communityPostRepository = communityPostRepository,
            communityCommentRepository = communityCommentRepository,
            uploadedMediaRepository = uploadedMediaRepository,
            cursorCodec = cursorCodec
        )
    }

    @Test
    fun `create root comment stores comment on active post`() {
        stubCreate()
        `when`(communityCommentRepository.save(any(CommunityComment::class.java))).thenAnswer { invocation ->
            val comment = invocation.arguments[0] as CommunityComment
            comment(id = rootCommentId, parent = comment.parentComment, author = comment.author, body = comment.body)
        }

        val result = service.create(createCommand(parentCommentId = null, body = "저도 궁금해요"))

        assertEquals(rootCommentId, result.id)
        val savedComment = capturedComment()
        assertEquals(postId, savedComment.post.id)
        assertNull(savedComment.parentComment)
        assertEquals(memberId, savedComment.author.id)
        assertEquals("저도 궁금해요", savedComment.body)
    }

    @Test
    fun `create reply stores one level reply under root comment`() {
        stubCreate()
        `when`(communityCommentRepository.findById(rootCommentId)).thenReturn(Optional.of(rootComment))
        `when`(communityCommentRepository.save(any(CommunityComment::class.java))).thenAnswer { invocation ->
            val comment = invocation.arguments[0] as CommunityComment
            comment(id = replyCommentId, parent = comment.parentComment, author = comment.author, body = comment.body)
        }

        val result = service.create(createCommand(parentCommentId = rootCommentId, body = "스크래치 작업을 해보세요"))

        assertEquals(replyCommentId, result.id)
        val savedComment = capturedComment()
        assertEquals(rootCommentId, savedComment.parentComment?.id)
        assertEquals("스크래치 작업을 해보세요", savedComment.body)
    }

    @Test
    fun `create reply rejects reply parent that is already a reply`() {
        stubCreate()
        `when`(communityCommentRepository.findById(replyCommentId)).thenReturn(Optional.of(replyComment))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(parentCommentId = replyCommentId))
        }

        assertEquals(ErrorCode.COMMUNITY_INVALID_REPLY_PARENT, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    @Test
    fun `create reply rejects deleted parent comment`() {
        val deletedParent = comment(id = rootCommentId, parent = null, author = member, body = "삭제 전", isDeleted = true)
        stubCreate()
        `when`(communityCommentRepository.findById(rootCommentId)).thenReturn(Optional.of(deletedParent))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(parentCommentId = rootCommentId))
        }

        assertEquals(ErrorCode.COMMUNITY_INVALID_REPLY_PARENT, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    @Test
    fun `delete soft deletes author comment`() {
        `when`(communityCommentRepository.findById(rootCommentId)).thenReturn(Optional.of(rootComment))

        service.delete(CommunityCommentCommand.Delete(memberId = memberId, commentId = rootCommentId))

        assertTrue(rootComment.isDeleted)
    }

    @Test
    fun `list returns root comment page with replies and next cursor`() {
        val newest = comment(id = rootCommentId, parent = null, author = member, body = "newest")
        val older = comment(
            id = UUID.fromString("00000000-0000-0000-0000-000000000203"),
            parent = null,
            author = member,
            body = "older"
        )
        val overflow = comment(
            id = UUID.fromString("00000000-0000-0000-0000-000000000204"),
            parent = null,
            author = member,
            body = "overflow"
        )
        val reply = comment(id = replyCommentId, parent = newest, author = otherMember, body = "reply")
        setCreatedAt(newest, LocalDateTime.of(2026, 6, 12, 11, 0))
        setCreatedAt(older, LocalDateTime.of(2026, 6, 12, 10, 0))
        setCreatedAt(overflow, LocalDateTime.of(2026, 6, 12, 9, 0))
        setCreatedAt(reply, LocalDateTime.of(2026, 6, 12, 11, 1))
        `when`(communityCommentRepository.findRootFirstPage(postId, PageRequest.of(0, 3)))
            .thenReturn(listOf(newest, older, overflow))
        `when`(communityCommentRepository.findRepliesByParentIds(listOf(rootCommentId, requireNotNull(older.id))))
            .thenReturn(listOf(reply))

        val page = service.list(postId = postId, cursor = null, size = 2)

        assertEquals(listOf("newest", "older"), page.items.map { it.body })
        assertEquals(listOf("reply"), page.items.first().replies.map { it.body })
        assertTrue(page.nextCursor?.isNotBlank() == true)
    }

    @Test
    fun `list passes decoded cursor to repository`() {
        val payload = CommunityCommentCursorPayload(
            createdAt = LocalDateTime.of(2026, 6, 12, 10, 0),
            id = rootCommentId
        )
        val cursor = cursorCodec.encode(payload)
        `when`(communityCommentRepository.findRootPageAfter(postId, payload.createdAt, payload.id, PageRequest.of(0, 21)))
            .thenReturn(emptyList())

        val page = service.list(postId = postId, cursor = cursor, size = 20)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `list rejects invalid page size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.list(postId = postId, cursor = null, size = 0)
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `list rejects malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.list(postId = postId, cursor = "not-a-valid-cursor", size = 20)
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    }

    @Test
    fun `deleted comment hides image url`() {
        val media = uploadedMedia(id = mediaId, owner = member)
        val deleted = comment(
            id = rootCommentId,
            parent = null,
            author = member,
            body = "before delete",
            media = media,
            isDeleted = true
        )
        setCreatedAt(deleted, LocalDateTime.of(2026, 6, 12, 11, 0))
        `when`(communityCommentRepository.findRootFirstPage(postId, PageRequest.of(0, 21)))
            .thenReturn(listOf(deleted))
        `when`(communityCommentRepository.findRepliesByParentIds(listOf(rootCommentId)))
            .thenReturn(emptyList())

        val page = service.list(postId = postId, cursor = null, size = 20)

        assertEquals("삭제된 댓글입니다.", page.items.single().body)
        assertNull(page.items.single().imageUrl)
    }

    @Test
    fun `create comment attaches optional media`() {
        val media = uploadedMedia(id = mediaId, owner = member)
        stubCreate()
        `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media))
        `when`(communityCommentRepository.save(any(CommunityComment::class.java))).thenAnswer { invocation ->
            val comment = invocation.arguments[0] as CommunityComment
            comment(
                id = rootCommentId,
                parent = comment.parentComment,
                author = comment.author,
                body = comment.body,
                media = comment.media
            )
        }

        service.create(createCommand(mediaId = mediaId))

        val savedComment = capturedComment()
        assertEquals(mediaId, savedComment.media?.id)
        assertEquals(UploadedMediaStatus.ATTACHED, media.status)
    }

    @Test
    fun `create comment rejects missing media`() {
        stubCreate()
        `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(mediaId = mediaId))
        }

        assertEquals(ErrorCode.MEDIA_NOT_FOUND, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    @Test
    fun `create comment rejects media owned by another member`() {
        stubCreate()
        `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.of(uploadedMedia(id = mediaId, owner = otherMember)))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(mediaId = mediaId))
        }

        assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    @Test
    fun `create comment rejects media with wrong usage type`() {
        val media = uploadedMedia(id = mediaId, owner = member, usageType = UploadedMediaUsageType.PROFILE)
        stubCreate()
        `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(mediaId = mediaId))
        }

        assertEquals(ErrorCode.MEDIA_USAGE_MISMATCH, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    @Test
    fun `create comment rejects already attached media`() {
        val media = uploadedMedia(id = mediaId, owner = member, status = UploadedMediaStatus.ATTACHED)
        stubCreate()
        `when`(uploadedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media))

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(mediaId = mediaId))
        }

        assertEquals(ErrorCode.MEDIA_NOT_ATTACHABLE, exception.errorCode)
        verify(communityCommentRepository, never()).save(any(CommunityComment::class.java))
    }

    private fun stubCreate() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(communityPostRepository.findByIdAndIsDeletedFalse(postId)).thenReturn(post)
    }

    private fun createCommand(
        parentCommentId: UUID? = null,
        body: String = "저도 궁금해요",
        mediaId: UUID? = null
    ): CommunityCommentCommand.Create =
        CommunityCommentCommand.Create(
            memberId = memberId,
            postId = postId,
            parentCommentId = parentCommentId,
            body = body,
            mediaId = mediaId
        )

    private fun comment(
        id: UUID,
        parent: CommunityComment?,
        author: Member,
        body: String,
        media: UploadedMedia? = null,
        isDeleted: Boolean = false
    ): CommunityComment =
        CommunityComment(
            id = id,
            post = post,
            parentComment = parent,
            author = author,
            body = body,
            media = media,
            isDeleted = isDeleted
        )

    private fun uploadedMedia(
        id: UUID,
        owner: Member,
        usageType: UploadedMediaUsageType = UploadedMediaUsageType.COMMUNITY_POST,
        status: UploadedMediaStatus = UploadedMediaStatus.TEMP
    ): UploadedMedia =
        UploadedMedia(
            id = id,
            owner = owner,
            mediaType = UploadedMediaType.IMAGE,
            usageType = usageType,
            fileUrl = "https://example.test/comment.jpg",
            cloudinaryPublicId = "community/comment",
            status = status
        )

    private fun capturedComment(): CommunityComment {
        val captor = ArgumentCaptor.forClass(CommunityComment::class.java)
        verify(communityCommentRepository).save(captor.capture())
        return captor.value
    }

    private fun setCreatedAt(entity: BaseTimeEntity, createdAt: LocalDateTime) {
        val field = BaseTimeEntity::class.java.getDeclaredField("createdAt")
        field.isAccessible = true
        field.set(entity, createdAt)
    }
}
