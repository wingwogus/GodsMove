package com.chamchamcham.api.search.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchResult
import com.chamchamcham.application.search.SearchService
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
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
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @MockBean private lateinit var searchService: SearchService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `search with category ALL returns sections`() {
        `when`(
            searchService.searchAll(
                SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 20)
            )
        ).thenReturn(
            SearchResult.Sections(
                sections = listOf(
                    SearchResult.SectionPreview(
                        category = SearchCategory.RECORD,
                        items = listOf(
                            SearchResult.Item(
                                category = SearchCategory.RECORD,
                                id = recordId,
                                title = "황기 · 수확",
                                snippet = "수확 완료",
                                thumbnailUrl = null,
                                createdAt = createdAt,
                            )
                        ),
                        hasMore = true,
                    ),
                    SearchResult.SectionPreview(
                        category = SearchCategory.POST,
                        items = emptyList(),
                        hasMore = false,
                    ),
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/search")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sections[0].category", equalTo("RECORD")))
            .andExpect(jsonPath("$.data.sections[0].items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.sections[0].hasMore", equalTo(true)))
    }

    @Test
    fun `search with category RECORD returns cursor page`() {
        `when`(
            searchService.search(
                SearchCategory.RECORD,
                SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10)
            )
        ).thenReturn(
            SearchResult.Page(
                items = listOf(
                    SearchResult.Item(
                        category = SearchCategory.RECORD,
                        id = recordId,
                        title = "황기 · 수확",
                        snippet = "수확 완료",
                        thumbnailUrl = null,
                        createdAt = createdAt,
                    )
                ),
                nextCursor = "cursor-2"
            )
        )

        mockMvc.perform(
            get("/api/v1/search")
                .with(authenticatedMember(memberId.toString()))
                .param("keyword", "황기")
                .param("category", "RECORD")
                .param("cursor", "cursor-1")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].id", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))
    }

    @Test
    fun `search without auth returns unauthorized`() {
        mockMvc.perform(get("/api/v1/search"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
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
}
