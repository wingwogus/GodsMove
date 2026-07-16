package com.chamchamcham.api.community.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.community.dto.CommunityRequests
import com.chamchamcham.api.community.dto.CommunityResponses
import com.chamchamcham.application.community.CommunityCommentCommand
import com.chamchamcham.application.community.CommunityCommentService
import com.chamchamcham.application.community.CommunityPostCommand
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/community")
class CommunityController(
    private val communityPostService: CommunityPostService,
    private val communityCommentService: CommunityCommentService
) {
    @GetMapping("/boards")
    fun listBoards(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<List<CommunityResponses.BoardResponse>>> {
        val boards = communityPostService.listBoards(parseMemberId(memberId))
        return ResponseEntity.ok(ApiResponse.ok(boards.map(CommunityResponses.BoardResponse::from)))
    }

    @GetMapping("/posts")
    fun listPosts(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) authorMemberId: UUID?,
        @RequestParam(required = false) cropId: UUID?,
        @RequestParam(required = false) postType: CommunityPostType?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "false") likedOnly: Boolean,
        @RequestParam(defaultValue = "false") mineOnly: Boolean,
        @RequestParam(defaultValue = "LATEST") sort: CommunityPostSort,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<CommunityResponses.PostPageResponse>> {
        val page = communityPostService.search(
            CommunityPostSearchCondition(
                memberId = parseOptionalMemberId(memberId),
                authorMemberId = authorMemberId,
                cropId = cropId,
                postType = postType,
                keyword = keyword,
                likedOnly = likedOnly,
                mineOnly = mineOnly,
                sort = sort,
                cursor = cursor,
                size = size
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.PostPageResponse.from(page)))
    }

    @PostMapping("/posts")
    fun createPost(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: CommunityRequests.SavePostRequest
    ): ResponseEntity<ApiResponse<CommunityResponses.PostIdResponse>> {
        val result = communityPostService.create(
            CommunityPostCommand.Create(
                memberId = parseMemberId(memberId),
                cropId = requireNotNull(request.cropId),
                postType = requireNotNull(request.postType),
                title = request.title,
                body = request.body,
                farmingRecordId = request.farmingRecordId,
                mediaIds = request.mediaIds
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.PostIdResponse.from(result)))
    }

    @GetMapping("/posts/{postId}")
    fun getPost(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable postId: UUID
    ): ResponseEntity<ApiResponse<CommunityResponses.PostDetailResponse>> {
        val detail = communityPostService.getDetail(parseOptionalMemberId(memberId), postId)
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.PostDetailResponse.from(detail)))
    }

    @PatchMapping("/posts/{postId}")
    fun updatePost(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable postId: UUID,
        @Valid @RequestBody request: CommunityRequests.SavePostRequest
    ): ResponseEntity<ApiResponse<CommunityResponses.PostIdResponse>> {
        val result = communityPostService.update(
            CommunityPostCommand.Update(
                memberId = parseMemberId(memberId),
                postId = postId,
                cropId = requireNotNull(request.cropId),
                postType = requireNotNull(request.postType),
                title = request.title,
                body = request.body,
                farmingRecordId = request.farmingRecordId,
                mediaIds = request.mediaIds
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.PostIdResponse.from(result)))
    }

    @DeleteMapping("/posts/{postId}")
    fun deletePost(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable postId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        communityPostService.delete(CommunityPostCommand.Delete(parseMemberId(memberId), postId))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @GetMapping("/posts/{postId}/comments")
    fun listComments(
        @PathVariable postId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<CommunityResponses.CommentPageResponse>> {
        val page = communityCommentService.list(postId, cursor, size)
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.CommentPageResponse.from(page)))
    }

    @PostMapping("/posts/{postId}/comments")
    fun createComment(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable postId: UUID,
        @Valid @RequestBody request: CommunityRequests.CreateCommentRequest
    ): ResponseEntity<ApiResponse<CommunityResponses.CommentIdResponse>> {
        val result = communityCommentService.create(
            CommunityCommentCommand.Create(
                memberId = parseMemberId(memberId),
                postId = postId,
                parentCommentId = request.parentCommentId,
                body = request.body,
                mediaId = request.mediaId
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.CommentIdResponse.from(result)))
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable commentId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        communityCommentService.delete(CommunityCommentCommand.Delete(parseMemberId(memberId), commentId))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/posts/{postId}/like-toggle")
    fun toggleLike(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable postId: UUID
    ): ResponseEntity<ApiResponse<CommunityResponses.LikeToggleResponse>> {
        val result = communityPostService.toggleLike(
            CommunityPostCommand.ToggleLike(parseMemberId(memberId), postId)
        )
        return ResponseEntity.ok(ApiResponse.ok(CommunityResponses.LikeToggleResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }

    private fun parseOptionalMemberId(memberId: String?): UUID? =
        memberId
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
