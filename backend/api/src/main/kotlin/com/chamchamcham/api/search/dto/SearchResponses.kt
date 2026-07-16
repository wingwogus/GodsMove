package com.chamchamcham.api.search.dto

import com.chamchamcham.api.community.dto.CommunityResponses
import com.chamchamcham.api.farming.dto.FarmingRecordResponses
import com.chamchamcham.application.search.SearchResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object SearchResponses {
    data class SearchPolicyItemResponse(
        val id: UUID,
        val title: String,
        val agencyName: String,
        val eligibilitySummary: String,
        val benefitSummary: String,
        val applicationPeriodLabel: String,
        val applyStartsOn: LocalDate?,
        val applyEndsOn: LocalDate?,
        val sourceUrl: String?,
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

    data class SearchRecordSectionResponse(
        val items: List<FarmingRecordResponses.RecordSummaryResponse>,
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.RecordPage): SearchRecordSectionResponse = SearchRecordSectionResponse(
                items = page.items.map(FarmingRecordResponses.RecordSummaryResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    data class SearchPolicySectionResponse(
        val items: List<SearchPolicyItemResponse>,
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PolicyPage): SearchPolicySectionResponse = SearchPolicySectionResponse(
                items = page.items.map(SearchPolicyItemResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    data class SearchPostSectionResponse(
        val items: List<CommunityResponses.PostSummaryResponse>,
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.PostPage): SearchPostSectionResponse = SearchPostSectionResponse(
                items = page.items.map(CommunityResponses.PostSummaryResponse::from),
                totalCount = page.totalCount,
            )
        }
    }

    data class SearchAllResponse(
        val records: SearchRecordSectionResponse,
        val policies: SearchPolicySectionResponse,
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

    data class SearchRecordPageResponse(
        val items: List<FarmingRecordResponses.RecordSummaryResponse>,
        val nextCursor: String?,
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

    data class SearchPolicyPageResponse(
        val items: List<SearchPolicyItemResponse>,
        val nextCursor: String?,
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

    data class SearchPostPageResponse(
        val items: List<CommunityResponses.PostSummaryResponse>,
        val nextCursor: String?,
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
