package com.chamchamcham.application.search

import com.chamchamcham.application.community.CommunityPostResult
import com.chamchamcham.application.farming.FarmingRecordResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object SearchResult {
    data class RecordPage(
        val items: List<FarmingRecordResult.Summary>,
        val nextCursor: String?,
        val totalCount: Long,
    )

    data class PostPage(
        val items: List<CommunityPostResult.PostSummary>,
        val nextCursor: String?,
        val totalCount: Long,
    )

    data class PolicyItem(
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
    )

    data class PolicyPage(
        val items: List<PolicyItem>,
        val nextCursor: String?,
        val totalCount: Long,
    )

    data class All(
        val records: RecordPage,
        val policies: PolicyPage,
        val posts: PostPage,
    )
}
