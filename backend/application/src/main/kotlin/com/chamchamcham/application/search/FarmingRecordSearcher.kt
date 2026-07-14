package com.chamchamcham.application.search

import com.chamchamcham.application.farming.FarmingRecordSearchCondition
import com.chamchamcham.application.farming.FarmingRecordService
import org.springframework.stereotype.Component

@Component
class FarmingRecordSearcher(
    private val farmingRecordService: FarmingRecordService,
) : CategorySearcher {
    override fun category(): SearchCategory = SearchCategory.RECORD

    override fun search(query: SearchQuery): SearchResult.Page {
        val page = farmingRecordService.search(
            FarmingRecordSearchCondition(
                memberId = query.memberId,
                cropIds = emptyList(),
                workTypes = emptyList(),
                startDate = null,
                endDate = null,
                keyword = query.keyword,
                cursor = query.cursor,
                size = query.size,
            )
        )
        return SearchResult.Page(
            items = page.items.map { summary ->
                SearchResult.Item(
                    category = SearchCategory.RECORD,
                    id = summary.id,
                    title = "${summary.cropName} · ${summary.workType.label}",
                    snippet = summary.memoPreview,
                    thumbnailUrl = summary.thumbnailUrl,
                    createdAt = summary.workedAt,
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}
