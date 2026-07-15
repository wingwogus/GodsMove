package com.chamchamcham.application.search

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SearchServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    private class StubSearcher(
        private val stubCategory: SearchCategory,
        private val page: SearchResult.Page,
    ) : CategorySearcher {
        var lastQuery: SearchQuery? = null
        override fun category(): SearchCategory = stubCategory
        override fun search(query: SearchQuery): SearchResult.Page {
            lastQuery = query
            return page
        }
    }

    private fun item(category: SearchCategory): SearchResult.Item =
        SearchResult.Item(
            category = category,
            id = UUID.randomUUID(),
            title = "title",
            snippet = "snippet",
            thumbnailUrl = null,
            createdAt = createdAt,
        )

    @Test
    fun `search dispatches to the matching category searcher`() {
        val recordSearcher = StubSearcher(
            SearchCategory.RECORD,
            SearchResult.Page(items = listOf(item(SearchCategory.RECORD)), nextCursor = "next", totalCount = 1)
        )
        val postSearcher = StubSearcher(
            SearchCategory.POST,
            SearchResult.Page(items = emptyList(), nextCursor = null, totalCount = 0)
        )
        val service = SearchService(listOf(recordSearcher, postSearcher))

        val query = SearchQuery(memberId = memberId, keyword = "황기", cursor = null, size = 10)
        val page = service.search(SearchCategory.RECORD, query)

        assertThat(page.items).hasSize(1)
        assertThat(page.items.single().category).isEqualTo(SearchCategory.RECORD)
        assertThat(page.nextCursor).isEqualTo("next")
        assertThat(recordSearcher.lastQuery).isEqualTo(query)
    }

    @Test
    fun `search throws for a category with no registered searcher`() {
        val service = SearchService(emptyList())

        val exception = assertThrows(BusinessException::class.java) {
            service.search(SearchCategory.RECORD, SearchQuery(memberId = memberId, keyword = null, cursor = null, size = 10))
        }

        assertEquals(ErrorCode.SEARCH_CATEGORY_NOT_SUPPORTED, exception.errorCode)
    }

    @Test
    fun `searchAll returns one preview section per registered category ordered record then post`() {
        val recordSearcher = StubSearcher(
            SearchCategory.RECORD,
            SearchResult.Page(items = listOf(item(SearchCategory.RECORD)), nextCursor = "more", totalCount = 12)
        )
        val postSearcher = StubSearcher(
            SearchCategory.POST,
            SearchResult.Page(items = listOf(item(SearchCategory.POST)), nextCursor = null, totalCount = 1)
        )
        val service = SearchService(listOf(postSearcher, recordSearcher))

        val sections = service.searchAll(SearchQuery(memberId = memberId, keyword = "황기", cursor = "ignored", size = 20)).sections

        assertThat(sections.map { it.category }).containsExactly(SearchCategory.RECORD, SearchCategory.POST)
        assertThat(sections[0].hasMore).isTrue()
        assertThat(sections[1].hasMore).isFalse()
        assertThat(sections[0].totalCount).isEqualTo(12)
        assertThat(sections[1].totalCount).isEqualTo(1)
        assertThat(recordSearcher.lastQuery?.size).isEqualTo(3)
        assertThat(recordSearcher.lastQuery?.cursor).isNull()
    }
}
