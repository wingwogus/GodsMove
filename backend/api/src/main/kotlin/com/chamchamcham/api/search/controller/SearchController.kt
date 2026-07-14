package com.chamchamcham.api.search.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.search.dto.SearchResponses
import com.chamchamcham.api.search.dto.SearchSuggestionResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchService
import com.chamchamcham.application.search.SearchSuggestionService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchService: SearchService,
    private val searchSuggestionService: SearchSuggestionService,
) {
    @GetMapping
    fun search(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "ALL") category: SearchCategory,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val query = SearchQuery(
            memberId = parseMemberId(memberId),
            keyword = keyword,
            cursor = cursor,
            size = size,
        )
        return if (category == SearchCategory.ALL) {
            ResponseEntity.ok(ApiResponse.ok(SearchResponses.SectionsResponse.from(searchService.searchAll(query))))
        } else {
            ResponseEntity.ok(ApiResponse.ok(SearchResponses.PageResponse.from(searchService.search(category, query))))
        }
    }

    @GetMapping("/suggestions")
    fun suggestions(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<*>> {
        parseMemberId(memberId)
        return ResponseEntity.ok(
            ApiResponse.ok(SearchSuggestionResponses.SuggestionsResponse.from(searchSuggestionService.suggest(keyword)))
        )
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
}
