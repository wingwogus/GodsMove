package com.chamchamcham.application.community

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.community.CommunityCommentRepository
import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostLike
import com.chamchamcham.domain.community.CommunityPostLikeRepository
import com.chamchamcham.domain.community.CommunityPostMedia
import com.chamchamcham.domain.community.CommunityPostMediaRepository
import com.chamchamcham.domain.community.CommunityPostQueryRepository
import com.chamchamcham.domain.community.CommunityPostRepository
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class CommunityPostService(
    private val memberRepository: MemberRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityPostMediaRepository: CommunityPostMediaRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val communityPostLikeRepository: CommunityPostLikeRepository,
    private val memberCropRepository: MemberCropRepository,
    private val communityPostQueryRepository: CommunityPostQueryRepository,
    private val cursorCodec: OpaqueCursorCodec
) {
    @Transactional(readOnly = true)
    fun listBoards(memberId: UUID): List<CommunityPostResult.Board> {
        val seen = linkedSetOf<UUID>()
        return memberCropRepository.findByMemberId(memberId)
            .map { it.crop }
            .filter { crop -> seen.add(requireNotNull(crop.id) { "Persisted crop id is required" }) }
            .map { crop ->
                CommunityPostResult.Board(
                    cropId = requireNotNull(crop.id) { "Persisted crop id is required" },
                    cropName = crop.name
                )
            }
    }

    @Transactional(readOnly = true)
    fun search(condition: CommunityPostSearchCondition): CommunityPostResult.Page {
        if (condition.memberId == null && (condition.likedOnly || condition.mineOnly)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        validatePageSize(condition.size)
        val cursor = decodeCursor(condition.sort, condition.cursor)
        val result = communityPostQueryRepository.search(
            CommunityPostQueryRepository.SearchCondition(
                memberId = condition.memberId,
                authorMemberId = condition.authorMemberId,
                cropIds = condition.cropIds,
                postType = condition.postType,
                keyword = condition.keyword,
                likedOnly = condition.likedOnly,
                mineOnly = condition.mineOnly,
                sort = condition.sort,
                cursor = cursor,
                size = condition.size + 1
            )
        )
        val visibleRows = result.rows.take(condition.size)
        val nextCursor = if (result.rows.size > condition.size) {
            visibleRows.lastOrNull()?.let { row -> encodeCursor(condition.sort, row) }
        } else {
            null
        }
        return CommunityPostResult.Page(
            items = visibleRows.map(::toSummary),
            nextCursor = nextCursor
        )
    }

    @Transactional(readOnly = true)
    fun count(condition: CommunityPostSearchCondition): Long {
        return communityPostQueryRepository.count(
            CommunityPostQueryRepository.SearchCondition(
                memberId = condition.memberId,
                authorMemberId = condition.authorMemberId,
                cropIds = condition.cropIds,
                postType = condition.postType,
                keyword = condition.keyword,
                likedOnly = condition.likedOnly,
                mineOnly = condition.mineOnly,
                sort = condition.sort,
                cursor = null,
                size = COUNT_QUERY_SIZE
            )
        )
    }

    private fun validatePageSize(size: Int) {
        if (size <= 0 || size == Int.MAX_VALUE) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    @Transactional(readOnly = true)
    fun getDetail(memberId: UUID?, postId: UUID): CommunityPostResult.PostDetail {
        val post = findPost(postId)
        val imageUrls = communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(postId)
            .map { it.uploadedMedia.fileUrl }
        return CommunityPostResult.PostDetail(
            id = requireNotNull(post.id) { "Persisted post id is required" },
            cropId = requireNotNull(post.crop.id) { "Persisted crop id is required" },
            cropName = post.crop.name,
            postType = post.postType,
            title = post.title,
            body = post.body,
            imageUrls = imageUrls,
            farmingRecordId = post.farmingRecord?.id,
            author = authorOf(post.author),
            commentCount = communityCommentRepository.countByPostIdAndIsDeletedFalse(postId),
            likeCount = communityPostLikeRepository.countByPostId(postId),
            likedByMe = memberId?.let {
                communityPostLikeRepository.existsByPostIdAndMemberId(postId, it)
            } ?: false,
            createdAt = post.createdAt
        )
    }

    fun create(command: CommunityPostCommand.Create): CommunityPostResult.PostId {
        val member = findMember(command.memberId)
        val crop = findCrop(command.cropId)
        val farmingRecord = resolveFarmingRecord(command.memberId, command.cropId, command.farmingRecordId)
        val media = validateNewMedia(command.memberId, command.mediaIds)

        val post = communityPostRepository.save(
            CommunityPost(
                author = member,
                crop = crop,
                farmingRecord = farmingRecord,
                postType = command.postType,
                title = command.title,
                body = command.body
            )
        )

        attachMedia(post, media)
        return CommunityPostResult.idOf(post)
    }

    fun update(command: CommunityPostCommand.Update): CommunityPostResult.PostId {
        val post = findPost(command.postId)
        assertAuthor(post, command.memberId)
        val crop = findCrop(command.cropId)
        val farmingRecord = resolveFarmingRecord(command.memberId, command.cropId, command.farmingRecordId)
        val existingPostMedia = communityPostMediaRepository.findByPostIdOrderByDisplayOrderAsc(command.postId)
        val media = validateUpdatedMedia(post, command.memberId, command.mediaIds)

        post.update(
            crop = crop,
            farmingRecord = farmingRecord,
            postType = command.postType,
            title = command.title,
            body = command.body
        )
        syncMedia(post, existingPostMedia, media)

        return CommunityPostResult.idOf(post)
    }

    fun delete(command: CommunityPostCommand.Delete) {
        val post = findPost(command.postId)
        assertAuthor(post, command.memberId)
        post.softDelete()
    }

    fun toggleLike(command: CommunityPostCommand.ToggleLike): CommunityPostResult.LikeToggle {
        val post = findPost(command.postId)
        val existing = communityPostLikeRepository.findByPostIdAndMemberId(command.postId, command.memberId)
        val liked = if (existing == null) {
            val member = findMember(command.memberId)
            communityPostLikeRepository.save(CommunityPostLike(post = post, member = member))
            true
        } else {
            communityPostLikeRepository.delete(existing)
            false
        }

        return CommunityPostResult.LikeToggle(
            liked = liked,
            likeCount = communityPostLikeRepository.countByPostId(command.postId)
        )
    }

    private fun validateNewMedia(memberId: UUID, mediaIds: List<UUID>): List<UploadedMedia> {
        validateDistinctMediaIds(mediaIds)
        if (mediaIds.isEmpty()) {
            return emptyList()
        }
        val mediaById = findMediaById(mediaIds)

        return mediaIds.map { mediaId ->
            val media = mediaById[mediaId] ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
            validateMediaOwnerAndUsage(memberId, media)
            if (!media.isAttachable()) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            media
        }
    }

    private fun validateUpdatedMedia(
        post: CommunityPost,
        memberId: UUID,
        mediaIds: List<UUID>
    ): List<UploadedMedia> {
        validateDistinctMediaIds(mediaIds)
        if (mediaIds.isEmpty()) {
            return emptyList()
        }

        val mediaById = findMediaById(mediaIds)
        val postMediaByMediaId = communityPostMediaRepository.findByUploadedMediaIdIn(mediaIds)
            .associateBy { requireNotNull(it.uploadedMedia.id) { "Persisted media id is required" } }
        val postId = requireNotNull(post.id) { "Persisted post id is required" }

        return mediaIds.map { mediaId ->
            val media = mediaById[mediaId] ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
            validateMediaOwnerAndUsage(memberId, media)

            val postMedia = postMediaByMediaId[mediaId]
            if (postMedia != null && postMedia.post.id != postId) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            if (!media.isAttachable() && media.status != UploadedMediaStatus.ATTACHED) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            if (media.status == UploadedMediaStatus.ATTACHED && postMedia?.post?.id != postId) {
                throw BusinessException(ErrorCode.MEDIA_NOT_ATTACHABLE)
            }
            media
        }
    }

    private fun validateDistinctMediaIds(mediaIds: List<UUID>) {
        if (mediaIds.size != mediaIds.toSet().size) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun findMediaById(mediaIds: List<UUID>): Map<UUID, UploadedMedia> =
        uploadedMediaRepository.findAllById(mediaIds)
            .associateBy { requireNotNull(it.id) { "Persisted media id is required" } }

    private fun validateMediaOwnerAndUsage(memberId: UUID, media: UploadedMedia) {
        if (media.owner.id != memberId) {
            throw BusinessException(ErrorCode.MEDIA_NOT_OWNED)
        }
        if (media.usageType != UploadedMediaUsageType.COMMUNITY_POST) {
            throw BusinessException(ErrorCode.MEDIA_USAGE_MISMATCH)
        }
    }

    private fun resolveFarmingRecord(
        memberId: UUID,
        cropId: UUID,
        farmingRecordId: UUID?
    ): FarmingRecord? {
        return farmingRecordId?.let { recordId ->
            val record = farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)
                ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)
            if (record.crop.id != cropId) {
                throw BusinessException(ErrorCode.COMMUNITY_FARMING_RECORD_CROP_MISMATCH)
            }
            record
        }
    }

    private fun assertAuthor(post: CommunityPost, memberId: UUID) {
        if (post.author.id != memberId) {
            throw BusinessException(ErrorCode.COMMUNITY_FORBIDDEN)
        }
    }

    private fun attachMedia(post: CommunityPost, media: List<UploadedMedia>) {
        if (media.isEmpty()) {
            return
        }
        media.forEach(UploadedMedia::markAttached)
        communityPostMediaRepository.saveAll(
            media.mapIndexed { index, uploadedMedia ->
                CommunityPostMedia(
                    post = post,
                    uploadedMedia = uploadedMedia,
                    displayOrder = index
                )
            }
        )
    }

    private fun syncMedia(
        post: CommunityPost,
        existingPostMedia: List<CommunityPostMedia>,
        media: List<UploadedMedia>
    ) {
        val finalMediaIds = media.mapTo(mutableSetOf()) {
            requireNotNull(it.id) { "Persisted media id is required" }
        }
        existingPostMedia
            .filter { requireNotNull(it.uploadedMedia.id) { "Persisted media id is required" } !in finalMediaIds }
            .forEach { it.uploadedMedia.markDeleted() }

        communityPostMediaRepository.deleteByPost(post)
        communityPostMediaRepository.flush()
        attachMedia(post, media)
    }

    private fun findPost(postId: UUID): CommunityPost =
        communityPostRepository.findByIdAndIsDeletedFalse(postId)
            ?: throw BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND)

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

    private fun findCrop(cropId: UUID): Crop =
        cropRepository.findById(cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }

    private fun decodeCursor(
        sort: CommunityPostSort,
        cursor: String?
    ): CommunityPostQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }

        val payload = cursorCodec.decode(cursor, CommunityPostCursorPayload::class.java)
        if (payload.sort != sort) {
            throw BusinessException(ErrorCode.INVALID_CURSOR)
        }
        if (sort != CommunityPostSort.LATEST && payload.score == null) {
            throw BusinessException(ErrorCode.INVALID_CURSOR)
        }

        return CommunityPostQueryRepository.Cursor(
            sort = payload.sort,
            score = payload.score,
            createdAt = payload.createdAt,
            id = payload.id
        )
    }

    private fun encodeCursor(
        sort: CommunityPostSort,
        row: CommunityPostQueryRepository.Row
    ): String {
        val post = row.post
        val score = if (sort == CommunityPostSort.LATEST) {
            null
        } else {
            checkNotNull(row.score) { "Cursor score is required for $sort sort" }
        }
        return cursorCodec.encode(
            CommunityPostCursorPayload(
                sort = sort,
                score = score,
                createdAt = post.createdAt,
                id = requireNotNull(post.id) { "Persisted post id is required" }
            )
        )
    }

    private fun toSummary(row: CommunityPostQueryRepository.Row): CommunityPostResult.PostSummary {
        val post = row.post
        return CommunityPostResult.PostSummary(
            id = requireNotNull(post.id) { "Persisted post id is required" },
            cropId = requireNotNull(post.crop.id) { "Persisted crop id is required" },
            cropName = post.crop.name,
            postType = post.postType,
            title = post.title,
            bodyPreview = post.body.take(BODY_PREVIEW_LENGTH),
            thumbnailUrl = row.thumbnailUrl,
            author = authorOf(post.author),
            commentCount = row.commentCount,
            likeCount = row.likeCount,
            likedByMe = row.likedByMe,
            createdAt = post.createdAt
        )
    }

    private fun authorOf(member: Member): CommunityPostResult.AuthorSummary =
        CommunityPostResult.AuthorSummary(
            memberId = requireNotNull(member.id) { "Persisted member id is required" },
            nickname = member.nickname,
            profileImageUrl = member.profileMedia?.fileUrl
        )

    private companion object {
        const val BODY_PREVIEW_LENGTH = 80
        const val COUNT_QUERY_SIZE = 1
    }
}
