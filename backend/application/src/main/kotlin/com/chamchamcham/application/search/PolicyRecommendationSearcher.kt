package com.chamchamcham.application.search

import com.chamchamcham.application.policy.recommendation.PolicyRecommendationService
import org.springframework.stereotype.Component

@Component
class PolicyRecommendationSearcher(
    private val policyRecommendationService: PolicyRecommendationService,
) : CategorySearcher {
    override fun category(): SearchCategory = SearchCategory.POLICY

    override fun search(query: SearchQuery): SearchResult.Page {
        val page = policyRecommendationService.searchRecommendations(
            query.memberId,
            query.keyword,
            query.cursor,
            query.size,
        )
        return SearchResult.Page(
            items = page.items.map { card ->
                SearchResult.Item(
                    category = SearchCategory.POLICY,
                    id = card.policyProgramId,
                    title = card.programTitle,
                    snippet = card.benefitSummary,
                    thumbnailUrl = null,
                    createdAt = card.createdAt,
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}
