package com.chamchamcham.application.search

import com.chamchamcham.application.community.CommunityPostSearchCondition
import com.chamchamcham.application.community.CommunityPostService
import com.chamchamcham.domain.community.CommunityPostSort
import org.springframework.stereotype.Component

@Component
class CommunityPostSearcher(
    private val communityPostService: CommunityPostService,
) : CategorySearcher {
    override fun category(): SearchCategory = SearchCategory.POST

    override fun search(query: SearchQuery): SearchResult.Page {
        val page = communityPostService.search(
            CommunityPostSearchCondition(
                memberId = query.memberId,
                cropId = null,
                postType = null,
                keyword = query.keyword,
                likedOnly = false,
                mineOnly = false,
                sort = CommunityPostSort.LATEST,
                cursor = query.cursor,
                size = query.size,
            )
        )
        return SearchResult.Page(
            items = page.items.map { summary ->
                SearchResult.Item(
                    category = SearchCategory.POST,
                    id = summary.id,
                    title = summary.title,
                    snippet = summary.bodyPreview,
                    thumbnailUrl = summary.thumbnailUrl,
                    createdAt = summary.createdAt,
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}
