package com.chamchamcham.api.search.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchResult
import com.chamchamcham.application.search.SearchService
import com.chamchamcham.application.search.SearchSuggestionResult
import com.chamchamcham.application.search.SearchSuggestionService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.community.CommunityPostType
import com.chamchamcham.domain.farming.WorkType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(SearchController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class SearchControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val policyId = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @MockBean private lateinit var searchService: SearchService
    @MockBean private lateinit var searchSuggestionService: SearchSuggestionService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `search all returns record policy and post sections`() {
        `when`(searchService.searchAll(memberId, "황기")).thenReturn(
            SearchResult.All(
                records = SearchResult.RecordPage(items = listOf(recordSummary()), nextCursor = null, totalCount = 12),
                policies = SearchResult.PolicyPage(items = listOf(policyItem()), nextCursor = null, totalCount = 7),
                posts = SearchResult.PostPage(items = listOf(postSummary()), nextCursor = null, totalCount = 5),
            )
        )

        mockMvc.perform(
            get("/api/v1/search")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.records.items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.records.items[0].cropName", equalTo("황기")))
            .andExpect(jsonPath("$.data.records.items[0].workType", equalTo("HARVEST")))
            .andExpect(jsonPath("$.data.records.totalCount", equalTo(12)))
            .andExpect(jsonPath("$.data.policies.items[0].id", equalTo(policyId.toString())))
            .andExpect(jsonPath("$.data.policies.items[0].agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.policies.totalCount", equalTo(7)))
            .andExpect(jsonPath("$.data.posts.items[0].id", equalTo(postId.toString())))
            .andExpect(jsonPath("$.data.posts.items[0].likeCount", equalTo(5)))
            .andExpect(jsonPath("$.data.posts.totalCount", equalTo(5)))
    }

    @Test
    fun `search records returns cursor page with default size 20`() {
        `when`(
            searchService.searchRecords(
                SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 20)
            )
        ).thenReturn(
            SearchResult.RecordPage(items = listOf(recordSummary()), nextCursor = "cursor-2", totalCount = 25)
        )

        mockMvc.perform(
            get("/api/v1/search/records")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.items[0].memoPreview", equalTo("수확 완료")))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(25)))
    }

    @Test
    fun `search policies returns policy fields and forwards cursor and size`() {
        `when`(
            searchService.searchPolicies(
                SearchQuery(memberId = memberId, keyword = "청년", cursor = "cursor-1", size = 10)
            )
        ).thenReturn(
            SearchResult.PolicyPage(items = listOf(policyItem()), nextCursor = null, totalCount = 1)
        )

        mockMvc.perform(
            get("/api/v1/search/policies")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "청년")
                .param("cursor", "cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].title", equalTo("청년 농업인 지원사업")))
            .andExpect(jsonPath("$.data.items[0].agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.items[0].eligibilitySummary", equalTo("만 40세 미만")))
            .andExpect(jsonPath("$.data.items[0].benefitSummary", equalTo("월 100만원 지원")))
            .andExpect(jsonPath("$.data.items[0].applicationPeriodLabel", equalTo("2026-06-01 ~ 2026-06-30")))
            .andExpect(jsonPath("$.data.items[0].sourceUrl", equalTo("https://policy.example.com/1")))
            .andExpect(jsonPath("$.data.nextCursor", nullValue()))
            .andExpect(jsonPath("$.data.totalCount", equalTo(1)))
    }

    @Test
    fun `search posts returns post summaries`() {
        `when`(
            searchService.searchPosts(
                SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 20)
            )
        ).thenReturn(
            SearchResult.PostPage(items = listOf(postSummary()), nextCursor = "cursor-2", totalCount = 5)
        )

        mockMvc.perform(
            get("/api/v1/search/posts")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].title", equalTo("황기 수확 후기")))
            .andExpect(jsonPath("$.data.items[0].cropName", equalTo("황기")))
            .andExpect(jsonPath("$.data.items[0].commentCount", equalTo(2)))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(5)))
    }

    @Test
    fun `search endpoints without auth return unauthorized`() {
        listOf("/api/v1/search", "/api/v1/search/records", "/api/v1/search/policies", "/api/v1/search/posts")
            .forEach { path ->
                mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
            }
    }

    @Test
    fun `suggestions returns keywords from suggestion service`() {
        `when`(searchSuggestionService.suggest("황기"))
            .thenReturn(SearchSuggestionResult.Suggestions(keywords = listOf("황기", "황기환", "황기차")))

        mockMvc.perform(
            get("/api/v1/search/suggestions")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.keywords[0]", equalTo("황기")))
            .andExpect(jsonPath("$.data.keywords.length()", equalTo(3)))
    }

    @Test
    fun `suggestions without auth returns unauthorized`() {
        mockMvc.perform(get("/api/v1/search/suggestions").param("keyword", "황기"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun recordSummary(): FarmingRecordResult.Summary = FarmingRecordResult.Summary(
        id = recordId,
        cropId = cropId,
        cropName = "황기",
        workType = WorkType.HARVEST,
        workedAt = createdAt,
        weatherCondition = "맑음",
        weatherTemperature = 23,
        memoPreview = "수확 완료",
        thumbnailUrl = null,
    )

    private fun postSummary(): CommunityPostResult.PostSummary = CommunityPostResult.PostSummary(
        id = postId,
        cropId = cropId,
        cropName = "황기",
        postType = CommunityPostType.GENERAL,
        title = "황기 수확 후기",
        bodyPreview = "올해 수확량이",
        thumbnailUrl = null,
        author = CommunityPostResult.AuthorSummary(
            memberId = memberId,
            nickname = "농부",
            profileImageUrl = null,
        ),
        commentCount = 2,
        likeCount = 5,
        likedByMe = false,
        createdAt = createdAt,
    )

    private fun policyItem(): SearchResult.PolicyItem = SearchResult.PolicyItem(
        id = policyId,
        title = "청년 농업인 지원사업",
        agencyName = "농림축산식품부",
        eligibilitySummary = "만 40세 미만",
        benefitSummary = "월 100만원 지원",
        applicationPeriodLabel = "2026-06-01 ~ 2026-06-30",
        applyStartsOn = LocalDate.of(2026, 6, 1),
        applyEndsOn = LocalDate.of(2026, 6, 30),
        sourceUrl = "https://policy.example.com/1",
        createdAt = createdAt,
    )

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
}
