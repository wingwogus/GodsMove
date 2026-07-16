package com.chamchamcham.api.search.dto

import com.chamchamcham.api.community.dto.CommunityResponses
import com.chamchamcham.api.farming.dto.FarmingRecordResponses
import com.chamchamcham.application.search.SearchResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object SearchResponses {
    @Schema(description = "정책 정보 검색 결과 항목")
    data class SearchPolicyItemResponse(
        @field:Schema(description = "정책 프로그램 ID")
        val id: UUID,
        @field:Schema(description = "정책 제목", example = "청년 농업인 영농정착 지원사업")
        val title: String,
        @field:Schema(description = "기관명", example = "농림축산식품부")
        val agencyName: String,
        @field:Schema(description = "대상자 요약", example = "만 40세 미만 청년 농업인")
        val eligibilitySummary: String,
        @field:Schema(description = "지원내용 요약", example = "월 최대 110만원 지원")
        val benefitSummary: String,
        @field:Schema(description = "접수기간 표시 문구", example = "2026-06-01 ~ 2026-06-30")
        val applicationPeriodLabel: String,
        @field:Schema(description = "접수 시작일. 미확정이면 null")
        val applyStartsOn: LocalDate?,
        @field:Schema(description = "접수 종료일. 상시·미확정이면 null")
        val applyEndsOn: LocalDate?,
        @field:Schema(description = "정책 사이트 URL", example = "https://www.nongupez.kr/policy/1234")
        val sourceUrl: String?,
        @field:Schema(description = "정책 등록 시각 (최신순 정렬 기준)")
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(item: SearchResult.PolicyItem): SearchPolicyItemResponse = SearchPolicyItemResponse(
                id = item.id,
                title = item.title,
                agencyName = item.agencyName,
                eligibilitySummary = item.eligibilitySummary,
                benefitSummary = item.benefitSummary,
                applicationPeriodLabel = item.applicationPeriodLabel,
                applyStartsOn = item.applyStartsOn,
                applyEndsOn = item.applyEndsOn,
                sourceUrl = item.sourceUrl,
                createdAt = item.createdAt,
            )
        }
    }

    @Schema(description = "전체 검색의 나의 일지 섹션 (최신순 최대 3건)")
    data class SearchRecordSectionResponse(
        @field:Schema(description = "일지 미리보기 목록. 기록 목록 조회 응답과 동일한 형태")
        val items: List<FarmingRecordResponses.RecordSummaryResponse>,
        @field:Schema(description = "검색 조건에 맞는 일지 전체 개수", example = "12")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.RecordPage): SearchRecordSectionResponse = SearchRecordSectionResponse(
                items = page.items.map(FarmingRecordResponses.RecordSummaryResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    @Schema(description = "전체 검색의 정책 정보 섹션 (최신순 최대 3건)")
    data class SearchPolicySectionResponse(
        @field:Schema(description = "정책 미리보기 목록")
        val items: List<SearchPolicyItemResponse>,
        @field:Schema(description = "검색 조건에 맞는 정책 전체 개수", example = "7")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PolicyPage): SearchPolicySectionResponse = SearchPolicySectionResponse(
                items = page.items.map(SearchPolicyItemResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    @Schema(description = "전체 검색의 게시물 섹션 (최신순 최대 3건)")
    data class SearchPostSectionResponse(
        @field:Schema(description = "게시물 미리보기 목록. 게시물 목록 조회 응답과 동일한 형태")
        val items: List<CommunityResponses.PostSummaryResponse>,
        @field:Schema(description = "검색 조건에 맞는 게시물 전체 개수", example = "5")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PostPage): SearchPostSectionResponse = SearchPostSectionResponse(
                items = page.items.map(CommunityResponses.PostSummaryResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    @Schema(description = "전체 검색 응답. 나의 일지·정책 정보·게시물 섹션을 각각 최신순 최대 3건씩 담습니다")
    data class SearchAllResponse(
        @field:Schema(description = "나의 일지 섹션")
        val records: SearchRecordSectionResponse,
        @field:Schema(description = "정책 정보 섹션")
        val policies: SearchPolicySectionResponse,
        @field:Schema(description = "게시물 섹션")
        val posts: SearchPostSectionResponse,
    ) {
        companion object {
            fun from(result: SearchResult.All): SearchAllResponse = SearchAllResponse(
                records = SearchRecordSectionResponse.from(result.records),
                policies = SearchPolicySectionResponse.from(result.policies),
                posts = SearchPostSectionResponse.from(result.posts),
            )
        }
    }

    @Schema(description = "나의 일지 검색 응답 (커서 기반 무한스크롤)")
    data class SearchRecordPageResponse(
        @field:Schema(description = "일지 목록. 기록 목록 조회 응답과 동일한 형태")
        val items: List<FarmingRecordResponses.RecordSummaryResponse>,
        @field:Schema(description = "다음 페이지 커서. 마지막 페이지면 null")
        val nextCursor: String?,
        @field:Schema(description = "검색 조건에 맞는 일지 전체 개수", example = "25")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.RecordPage): SearchRecordPageResponse = SearchRecordPageResponse(
                items = page.items.map(FarmingRecordResponses.RecordSummaryResponse::from),
                nextCursor = page.nextCursor,
                totalCount = page.totalCount,
            )
        }
    }

    @Schema(description = "정책 정보 검색 응답 (커서 기반 무한스크롤)")
    data class SearchPolicyPageResponse(
        @field:Schema(description = "정책 목록")
        val items: List<SearchPolicyItemResponse>,
        @field:Schema(description = "다음 페이지 커서. 마지막 페이지면 null")
        val nextCursor: String?,
        @field:Schema(description = "검색 조건에 맞는 정책 전체 개수", example = "7")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PolicyPage): SearchPolicyPageResponse = SearchPolicyPageResponse(
                items = page.items.map(SearchPolicyItemResponse::from),
                nextCursor = page.nextCursor,
                totalCount = page.totalCount,
            )
        }
    }

    @Schema(description = "게시물 검색 응답 (커서 기반 무한스크롤)")
    data class SearchPostPageResponse(
        @field:Schema(description = "게시물 목록. 게시물 목록 조회 응답과 동일한 형태")
        val items: List<CommunityResponses.PostSummaryResponse>,
        @field:Schema(description = "다음 페이지 커서. 마지막 페이지면 null")
        val nextCursor: String?,
        @field:Schema(description = "검색 조건에 맞는 게시물 전체 개수", example = "5")
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PostPage): SearchPostPageResponse = SearchPostPageResponse(
                items = page.items.map(CommunityResponses.PostSummaryResponse::from),
                nextCursor = page.nextCursor,
                totalCount = page.totalCount,
            )
        }
    }
}
