package com.chamchamcham.api.search.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.search.dto.SearchResponses
import com.chamchamcham.api.search.dto.SearchSuggestionResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.search.SearchQuery
import com.chamchamcham.application.search.SearchService
import com.chamchamcham.application.search.SearchSuggestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Search", description = "통합 검색 API")
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchService: SearchService,
    private val searchSuggestionService: SearchSuggestionService,
) {
    @Operation(
        summary = "전체 검색",
        description = "나의 일지·정책 정보·게시물 3개 섹션을 한 번에 검색합니다. " +
            "각 섹션은 최신순 최대 3건과 전체 개수(totalCount)를 담습니다. " +
            "더 많은 결과는 카테고리별 검색 API(/records, /policies, /posts)로 조회합니다."
    )
    @GetMapping
    fun searchAll(
        @Parameter(hidden = true) @AuthenticationPrincipal memberId: String?,
        @Parameter(description = "검색어. 없으면 각 섹션의 최신순 목록을 반환합니다.")
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<SearchResponses.SearchAllResponse>> {
        val result = searchService.searchAll(parseMemberId(memberId), keyword)
        return ResponseEntity.ok(ApiResponse.ok(SearchResponses.SearchAllResponse.from(result)))
    }

    @Operation(
        summary = "나의 일지 검색",
        description = "로그인한 회원이 작성한 영농일지를 최신 작업일순으로 검색합니다. " +
            "검색어는 작물명·메모·비료명·농약명·작업유형에 부분일치로 매칭됩니다. " +
            "응답 항목은 기록 목록 조회와 동일한 형태이며 무한스크롤용 nextCursor를 제공합니다."
    )
    @GetMapping("/records")
    fun searchRecords(
        @Parameter(hidden = true) @AuthenticationPrincipal memberId: String?,
        @Parameter(description = "검색어. 없으면 최신순 전체 목록을 반환합니다.")
        @RequestParam(required = false) keyword: String?,
        @Parameter(description = "다음 페이지 커서. 첫 페이지는 생략합니다.")
        @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (기본 20)")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<SearchResponses.SearchRecordPageResponse>> {
        val result = searchService.searchRecords(
            SearchQuery(memberId = parseMemberId(memberId), keyword = keyword, cursor = cursor, size = size)
        )
        return ResponseEntity.ok(ApiResponse.ok(SearchResponses.SearchRecordPageResponse.from(result)))
    }

    @Operation(
        summary = "정책 정보 검색",
        description = "등록된 전체 정책(상세 동기화 완료 건)을 최신 등록순으로 검색합니다. " +
            "검색어는 정책 제목·기관명·요약에 부분일치로 매칭됩니다. " +
            "무한스크롤용 nextCursor를 제공합니다."
    )
    @GetMapping("/policies")
    fun searchPolicies(
        @Parameter(hidden = true) @AuthenticationPrincipal memberId: String?,
        @Parameter(description = "검색어. 없으면 최신순 전체 목록을 반환합니다.")
        @RequestParam(required = false) keyword: String?,
        @Parameter(description = "다음 페이지 커서. 첫 페이지는 생략합니다.")
        @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (기본 20, 1~50)")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<SearchResponses.SearchPolicyPageResponse>> {
        val result = searchService.searchPolicies(
            SearchQuery(memberId = parseMemberId(memberId), keyword = keyword, cursor = cursor, size = size)
        )
        return ResponseEntity.ok(ApiResponse.ok(SearchResponses.SearchPolicyPageResponse.from(result)))
    }

    @Operation(
        summary = "게시물 검색",
        description = "커뮤니티 게시물을 최신 작성순으로 검색합니다. " +
            "검색어는 제목·본문에 부분일치로 매칭됩니다. " +
            "응답 항목은 게시물 목록 조회와 동일한 형태이며 무한스크롤용 nextCursor를 제공합니다."
    )
    @GetMapping("/posts")
    fun searchPosts(
        @Parameter(hidden = true) @AuthenticationPrincipal memberId: String?,
        @Parameter(description = "검색어. 없으면 최신순 전체 목록을 반환합니다.")
        @RequestParam(required = false) keyword: String?,
        @Parameter(description = "다음 페이지 커서. 첫 페이지는 생략합니다.")
        @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (기본 20)")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<SearchResponses.SearchPostPageResponse>> {
        val result = searchService.searchPosts(
            SearchQuery(memberId = parseMemberId(memberId), keyword = keyword, cursor = cursor, size = size)
        )
        return ResponseEntity.ok(ApiResponse.ok(SearchResponses.SearchPostPageResponse.from(result)))
    }

    @Operation(
        summary = "연관 검색어",
        description = "입력 중인 keyword에 대한 연관 검색어 목록을 반환합니다. " +
            "작물명·농약 상표명·정책 제목·기관명에서 부분일치로 후보를 모아 최대 9건을 제공하며, " +
            "목록의 첫 번째 요소는 입력한 검색어 자신입니다."
    )
    @GetMapping("/suggestions")
    fun suggestions(
        @Parameter(hidden = true) @AuthenticationPrincipal memberId: String?,
        @Parameter(description = "입력 중인 검색어")
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<SearchSuggestionResponses.SuggestionsResponse>> {
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
