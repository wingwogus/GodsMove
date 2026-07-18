package com.chamchamcham.api.community.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.community.CommunityCommentCommand
import com.chamchamcham.application.community.CommunityCommentResult
import com.chamchamcham.application.community.CommunityCommentService
import com.chamchamcham.application.community.CommunityPostCommand
import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(CommunityController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class CommunityControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val authorMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondCropId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val commentId = UUID.fromString("00000000-0000-0000-0000-000000000501")
    private val createdAt = LocalDateTime.of(2026, 6, 12, 9, 0)

    @MockBean private lateinit var communityPostService: CommunityPostService
    @MockBean private lateinit var communityCommentService: CommunityCommentService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `create post maps request to command and returns post id`() {
        `when`(communityPostService.create(createPostCommand())).thenReturn(CommunityPostResult.PostId(postId))

        mockMvc.perform(
            post("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(savePostJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(postId.toString())))
    }

    @Test
    fun `create post rejects more than five media ids`() {
        val mediaIds = List(6) { "\"${UUID.randomUUID()}\"" }.joinToString(",")

        mockMvc.perform(
            post("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(savePostJson(mediaIdsJson = "[$mediaIds]"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("mediaIds")))
    }

    @Test
    fun `list post crops returns crops for the path member id not the authenticated caller`() {
        `when`(communityPostService.listPostCrops(authorMemberId)).thenReturn(
            listOf(
                CommunityPostResult.Board(cropId = cropId, cropName = "황기"),
                CommunityPostResult.Board(cropId = secondCropId, cropName = "인삼")
            )
        )

        mockMvc.perform(
            get("/api/v1/community/members/$authorMemberId/post-crops")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].cropId", equalTo(cropId.toString())))
            .andExpect(jsonPath("$.data[1].cropId", equalTo(secondCropId.toString())))
    }

    @Test
    fun `list posts returns cursor page`() {
        `when`(communityPostService.search(anySearchCondition())).thenReturn(postPageResult())

        mockMvc.perform(
            get("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("size", "20")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(postId.toString())))
            .andExpect(jsonPath("$.data.items[0].likedByMe", equalTo(true)))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
    }

    @Test
    fun `guest list maps null viewer`() {
        `when`(communityPostService.search(anySearchCondition(memberId = null)))
            .thenReturn(CommunityPostResult.Page(emptyList(), null))

        mockMvc.perform(get("/api/v1/community/posts"))
            .andExpect(status().isOk)
    }

    @Test
    fun `list posts maps sort and cursor parameters`() {
        `when`(
            communityPostService.search(
                CommunityPostSearchCondition(
                    memberId = memberId,
                    cropIds = listOf(cropId),
                    postType = CommunityPostType.QUESTION,
                    keyword = "황기",
                    likedOnly = true,
                    mineOnly = false,
                    sort = CommunityPostSort.POPULAR,
                    cursor = "cursor-1",
                    size = 10
                )
            )
        ).thenReturn(postPageResult())

        mockMvc.perform(
            get("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("cropId", cropId.toString())
                .param("postType", "QUESTION")
                .param("keyword", "황기")
                .param("likedOnly", "true")
                .param("sort", "POPULAR")
                .param("cursor", "cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-1")))
    }

    @Test
    fun `list posts maps multiple crop ids`() {
        val cropIds = listOf(cropId, secondCropId)
        `when`(
            communityPostService.search(
                CommunityPostSearchCondition(
                    memberId = memberId,
                    cropIds = cropIds,
                    postType = null,
                    keyword = null,
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = null,
                    size = 20,
                ),
            ),
        ).thenReturn(postPageResult())

        mockMvc.perform(
            get("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("cropId", cropId.toString(), secondCropId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(postId.toString())))
    }

    @Test
    fun `list posts maps author member id separately from authenticated member id`() {
        `when`(
            communityPostService.search(
                CommunityPostSearchCondition(
                    memberId = memberId,
                    authorMemberId = authorMemberId,
                    cropIds = emptyList(),
                    postType = null,
                    keyword = null,
                    likedOnly = false,
                    mineOnly = false,
                    sort = CommunityPostSort.LATEST,
                    cursor = null,
                    size = 20
                )
            )
        ).thenReturn(postPageResult())

        mockMvc.perform(
            get("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("authorMemberId", authorMemberId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(postId.toString())))
    }

    @Test
    fun `list posts returns invalid input for invalid sort parameter`() {
        mockMvc.perform(
            get("/api/v1/community/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("sort", "BAD_VALUE")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
            .andExpect(jsonPath("$.error.detail.field", equalTo("sort")))
    }

    @Test
    fun `get post detail returns detail`() {
        `when`(communityPostService.getDetail(memberId, postId)).thenReturn(postDetailResult())

        mockMvc.perform(
            get("/api/v1/community/posts/{postId}", postId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title", equalTo("황기 발아율 질문")))
    }

    @Test
    fun `guest detail maps null viewer`() {
        `when`(communityPostService.getDetail(null, postId)).thenReturn(postDetailResult(likedByMe = false))

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likedByMe", equalTo(false)))
    }

    @Test
    fun `update post maps request to command`() {
        `when`(communityPostService.update(updatePostCommand())).thenReturn(CommunityPostResult.PostId(postId))

        mockMvc.perform(
            patch("/api/v1/community/posts/{postId}", postId)
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(savePostJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(postId.toString())))
    }

    @Test
    fun `delete post delegates to service`() {
        mockMvc.perform(
            delete("/api/v1/community/posts/{postId}", postId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
    }

    @Test
    fun `create comment maps request to command`() {
        `when`(communityCommentService.create(createCommentCommand()))
            .thenReturn(CommunityCommentResult.CommentId(commentId))

        mockMvc.perform(
            post("/api/v1/community/posts/{postId}/comments", postId)
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"저도 궁금해요","mediaId":"$mediaId"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id", equalTo(commentId.toString())))
    }

    @Test
    fun `list comments returns cursor page with deleted comment body from service result`() {
        `when`(communityCommentService.list(postId, null, 20)).thenReturn(commentPageResult())

        mockMvc.perform(
            get("/api/v1/community/posts/{postId}/comments", postId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].body", equalTo("삭제된 댓글입니다.")))
            .andExpect(jsonPath("$.data.items[0].imageUrl", nullValue()))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("comment-cursor-1")))
    }

    @Test
    fun `list comments maps cursor and size parameters`() {
        `when`(communityCommentService.list(postId, "comment-cursor-1", 10)).thenReturn(commentPageResult(nextCursor = null))

        mockMvc.perform(
            get("/api/v1/community/posts/{postId}/comments", postId)
                .with(authenticatedMember(memberId.toString()))
                .param("cursor", "comment-cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nextCursor", nullValue()))
    }

    @Test
    fun `toggle like returns liked state and count`() {
        `when`(communityPostService.toggleLike(CommunityPostCommand.ToggleLike(memberId, postId)))
            .thenReturn(CommunityPostResult.LikeToggle(liked = true, likeCount = 9))

        mockMvc.perform(
            post("/api/v1/community/posts/{postId}/like-toggle", postId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.liked", equalTo(true)))
            .andExpect(jsonPath("$.data.likeCount", equalTo(9)))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }

    private fun savePostJson(mediaIdsJson: String = "[\"$mediaId\"]"): String {
        return """
            {
              "cropId":"$cropId",
              "postType":"QUESTION",
              "title":"황기 발아율 질문",
              "body":"싹이 거의 올라오지 않아요.",
              "farmingRecordId":"$recordId",
              "mediaIds":$mediaIdsJson
            }
        """.trimIndent()
    }

    private fun createPostCommand(): CommunityPostCommand.Create =
        CommunityPostCommand.Create(
            memberId = memberId,
            cropId = cropId,
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율 질문",
            body = "싹이 거의 올라오지 않아요.",
            farmingRecordId = recordId,
            mediaIds = listOf(mediaId)
        )

    private fun updatePostCommand(): CommunityPostCommand.Update =
        CommunityPostCommand.Update(
            memberId = memberId,
            postId = postId,
            cropId = cropId,
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율 질문",
            body = "싹이 거의 올라오지 않아요.",
            farmingRecordId = recordId,
            mediaIds = listOf(mediaId)
        )

    private fun createCommentCommand(): CommunityCommentCommand.Create =
        CommunityCommentCommand.Create(
            memberId = memberId,
            postId = postId,
            parentCommentId = null,
            body = "저도 궁금해요",
            mediaId = mediaId
        )

    private fun postPageResult(): CommunityPostResult.Page =
        CommunityPostResult.Page(
            items = listOf(postSummaryResult()),
            nextCursor = "cursor-1"
        )

    private fun postDetailResult(likedByMe: Boolean = true): CommunityPostResult.PostDetail =
        CommunityPostResult.PostDetail(
            id = postId,
            cropId = cropId,
            cropName = "황기",
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율 질문",
            body = "싹이 거의 올라오지 않아요.",
            imageUrls = listOf("https://example.test/1.jpg"),
            farmingRecordId = recordId,
            author = authorResult(),
            commentCount = 3,
            likeCount = 8,
            likedByMe = likedByMe,
            createdAt = createdAt
        )

    private fun postSummaryResult(): CommunityPostResult.PostSummary =
        CommunityPostResult.PostSummary(
            id = postId,
            cropId = cropId,
            cropName = "황기",
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율 질문",
            bodyPreview = "싹이 거의 올라오지 않아요.",
            thumbnailUrl = "https://example.test/1.jpg",
            author = authorResult(),
            commentCount = 3,
            likeCount = 8,
            likedByMe = true,
            createdAt = createdAt
        )

    private fun commentPageResult(nextCursor: String? = "comment-cursor-1"): CommunityCommentResult.Page =
        CommunityCommentResult.Page(
            items = listOf(deletedCommentResult()),
            nextCursor = nextCursor
        )

    private fun deletedCommentResult(): CommunityCommentResult.Comment =
        CommunityCommentResult.Comment(
            id = commentId,
            parentCommentId = null,
            author = authorResult(),
            body = "삭제된 댓글입니다.",
            imageUrl = null,
            deleted = true,
            createdAt = createdAt
        )

    private fun authorResult(): CommunityPostResult.AuthorSummary =
        CommunityPostResult.AuthorSummary(
            memberId = memberId,
            nickname = "황기농부",
            profileImageUrl = "https://example.test/profile.jpg"
        )

    private fun anySearchCondition(memberId: UUID? = this.memberId): CommunityPostSearchCondition =
        CommunityPostSearchCondition(
            memberId = memberId,
            cropIds = emptyList(),
            postType = null,
            keyword = null,
            likedOnly = false,
            mineOnly = false,
            sort = CommunityPostSort.LATEST,
            cursor = null,
            size = 20
        )
}
