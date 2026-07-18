package com.chamchamcham.config

import com.chamchamcham.api.community.controller.CommunityController
import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.api.security.CustomAccessDeniedHandler
import com.chamchamcham.api.security.CustomAuthenticationEntryPoint
import com.chamchamcham.application.community.CommunityCommentResult
import com.chamchamcham.application.community.CommunityCommentService
import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.community.CommunityPostSort
import com.chamchamcham.domain.community.CommunityPostType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(CommunityController::class)
@Import(
    SecurityConfig::class,
    CustomAuthenticationEntryPoint::class,
    CustomAccessDeniedHandler::class,
    MDCLoggingFilter::class,
    GlobalExceptionHandler::class
)
class SecurityConfigCommunityTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val authorMemberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val commentId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @MockBean private lateinit var communityPostService: CommunityPostService
    @MockBean private lateinit var communityCommentService: CommunityCommentService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `guest can read post list detail and comments`() {
        `when`(communityPostService.search(guestSearchCondition()))
            .thenReturn(CommunityPostResult.Page(emptyList(), null))
        `when`(communityPostService.getDetail(null, postId)).thenReturn(postDetail())
        `when`(communityCommentService.list(postId, null, 20))
            .thenReturn(CommunityCommentResult.Page(emptyList(), null))

        mockMvc.perform(get("/api/v1/community/posts"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/community/posts/{postId}/comments", postId))
            .andExpect(status().isOk)
    }

    @Test
    fun `guest cannot access community write routes`() {
        mockMvc.perform(
            post("/api/v1/community/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/api/v1/community/posts/{postId}", postId))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/v1/community/posts/{postId}/comments", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/api/v1/community/comments/{commentId}", commentId))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/v1/community/posts/{postId}/like-toggle", postId))
            .andExpect(status().isUnauthorized)
    }

    private fun guestSearchCondition(): CommunityPostSearchCondition =
        CommunityPostSearchCondition(
            memberId = null,
            cropIds = emptyList(),
            postType = null,
            keyword = null,
            likedOnly = false,
            mineOnly = false,
            sort = CommunityPostSort.LATEST,
            cursor = null,
            size = 20
        )

    private fun postDetail(): CommunityPostResult.PostDetail =
        CommunityPostResult.PostDetail(
            id = postId,
            cropId = cropId,
            cropName = "황기",
            postType = CommunityPostType.QUESTION,
            title = "황기 발아율 질문",
            body = "싹이 거의 올라오지 않아요.",
            imageUrls = emptyList(),
            farmingRecordId = null,
            author = CommunityPostResult.AuthorSummary(
                memberId = authorMemberId,
                nickname = "황기농부",
                profileImageUrl = null
            ),
            commentCount = 0,
            likeCount = 0,
            likedByMe = false,
            createdAt = LocalDateTime.of(2026, 6, 12, 9, 0)
        )
}
