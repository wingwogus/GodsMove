package com.chamchamcham.application.community

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.community.CommunityComment
import com.chamchamcham.domain.community.CommunityCommentRepository
import com.chamchamcham.domain.community.CommunityPostRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class CommunityCommentService(
    private val memberRepository: MemberRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val cursorCodec: OpaqueCursorCodec
) {
    fun create(command: CommunityCommentCommand.Create): CommunityCommentResult.CommentId {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val post = communityPostRepository.findByIdAndIsDeletedFalse(command.postId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND)
        val parentComment = command.parentCommentId?.let(::findValidParent)
        val media = command.mediaId?.let { validateMedia(command.memberId, it) }

        val comment = communityCommentRepository.save(
            CommunityComment(
                post = post,
                parentComment = parentComment,
                author = member,
                body = command.body,
                media = media
            )
        )
        media?.markAttached()

        return CommunityCommentResult.CommentId(
            requireNotNull(comment.id) { "Persisted comment id is required" }
        )
    }

    fun delete(command: CommunityCommentCommand.Delete) {
        val comment = communityCommentRepository.findById(command.commentId).orElseThrow {
            BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND)
        }
        if (comment.author.id != command.memberId) {
            throw BusinessException(ErrorCode.COMMUNITY_FORBIDDEN)
        }
        comment.softDelete()
    }

    @Transactional(readOnly = true)
    fun list(postId: UUID, cursor: String?, size: Int): CommunityCommentResult.Page {
        validatePageSize(size)
        val decodedCursor = decodeCursor(cursor)
        val pageable = PageRequest.of(0, size + 1)
        val rootComments = if (decodedCursor == null) {
            communityCommentRepository.findRootFirstPage(postId, pageable)
        } else {
            communityCommentRepository.findRootPageAfter(
                postId = postId,
                cursorCreatedAt = decodedCursor.createdAt,
                cursorId = decodedCursor.id,
                pageable = pageable
            )
        }
        val visibleRoots = rootComments.take(size)
        val rootIds = visibleRoots.map { requireNotNull(it.id) { "Persisted comment id is required" } }
        val repliesByParentId = if (rootIds.isEmpty()) {
            emptyMap()
        } else {
            communityCommentRepository.findRepliesByParentIds(rootIds)
                .groupBy { requireNotNull(it.parentComment?.id) { "Persisted parent comment id is required" } }
        }
        val nextCursor = if (rootComments.size > size) {
            visibleRoots.lastOrNull()?.let(::encodeCursor)
        } else {
            null
        }

        return CommunityCommentResult.Page(
            items = visibleRoots.map { root ->
                toResult(
                    comment = root,
                    replies = repliesByParentId[requireNotNull(root.id) { "Persisted comment id is required" }]
                        .orEmpty()
                        .map { toResult(it) }
                )
            },
            nextCursor = nextCursor
        )
    }

    private fun findValidParent(parentCommentId: UUID): CommunityComment {
        val parent = communityCommentRepository.findById(parentCommentId).orElseThrow {
            BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND)
        }
        if (parent.isDeleted || parent.parentComment != null) {
            throw BusinessException(ErrorCode.COMMUNITY_INVALID_REPLY_PARENT)
        }
        return parent
    }

    private fun validatePageSize(size: Int) {
        if (size <= 0 || size == Int.MAX_VALUE) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun decodeCursor(cursor: String?): CommunityCommentCursorPayload? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        return cursorCodec.decode(cursor, CommunityCommentCursorPayload::class.java)
    }

    private fun encodeCursor(comment: CommunityComment): String =
        cursorCodec.encode(
            CommunityCommentCursorPayload(
                createdAt = comment.createdAt,
                id = requireNotNull(comment.id) { "Persisted comment id is required" }
            )
        )

    private fun validateMedia(memberId: UUID, mediaId: UUID): UploadedMedia {
        val media = uploadedMediaRepository.findById(mediaId).orElseThrow {
            BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        }
        if (media.owner.id != memberId) {
            throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
        }
        if (media.usageType != UploadedMediaUsageType.COMMUNITY_POST) {
            throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
        }
        if (!media.isAttachable()) {
            throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
        }
        return media
    }

    private fun toResult(
        comment: CommunityComment,
        replies: List<CommunityCommentResult.Comment> = emptyList()
    ): CommunityCommentResult.Comment =
        CommunityCommentResult.Comment(
            id = requireNotNull(comment.id) { "Persisted comment id is required" },
            parentCommentId = comment.parentComment?.id,
            author = authorOf(comment.author),
            body = if (comment.isDeleted) DELETED_COMMENT_BODY else comment.body,
            imageUrl = if (comment.isDeleted) null else comment.media?.fileUrl,
            deleted = comment.isDeleted,
            createdAt = comment.createdAt,
            replies = replies
        )

    private fun authorOf(member: Member): CommunityPostResult.AuthorSummary =
        CommunityPostResult.AuthorSummary(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            nickname = member.nickname,
            profileImageUrl = member.profileMedia?.fileUrl
        )

    private companion object {
        const val DELETED_COMMENT_BODY = "삭제된 댓글입니다."
    }
}
