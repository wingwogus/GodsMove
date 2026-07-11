package com.chamchamcham.application.search

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.stereotype.Service

@Service
class SearchService(
    searchers: List<CategorySearcher>,
) {
    private val searchersByCategory: Map<SearchCategory, CategorySearcher> =
        searchers.associateBy { it.category() }

    fun search(category: SearchCategory, query: SearchQuery): SearchResult.Page {
        val searcher = searchersByCategory[category]
            ?: throw BusinessException(ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED)
        return searcher.search(query)
    }

    fun searchAll(query: SearchQuery): SearchResult.Sections {
        val sections = searchersByCategory.values
            .sortedBy { it.category().ordinal }
            .map { searcher ->
                val page = searcher.search(query.copy(size = PREVIEW_SIZE, cursor = null))
                SearchResult.SectionPreview(
                    category = searcher.category(),
                    items = page.items,
                    hasMore = page.nextCursor != null,
                )
            }
        return SearchResult.Sections(sections = sections)
    }

    private companion object {
        const val PREVIEW_SIZE = 5
    }
}
