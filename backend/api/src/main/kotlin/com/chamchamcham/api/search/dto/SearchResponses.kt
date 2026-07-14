package com.chamchamcham.api.search.dto

import com.chamchamcham.application.search.SearchCategory
import com.chamchamcham.application.search.SearchResult
import java.time.LocalDateTime
import java.util.UUID

object SearchResponses {
    data class ItemResponse(
        val category: SearchCategory,
        val id: UUID,
        val title: String,
        val snippet: String,
        val thumbnailUrl: String?,
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(item: SearchResult.Item): ItemResponse = ItemResponse(
                category = item.category,
                id = item.id,
                title = item.title,
                snippet = item.snippet,
                thumbnailUrl = item.thumbnailUrl,
                createdAt = item.createdAt,
            )
        }
    }

    data class PageResponse(
        val items: List<ItemResponse>,
        val nextCursor: String?,
        val totalCount: Long,
    ) {
        companion object {
            fun from(page: SearchResult.Page): PageResponse = PageResponse(
                items = page.items.map(ItemResponse::from),
                nextCursor = page.nextCursor,
                totalCount = page.totalCount,
            )
        }
    }

    data class SectionResponse(
        val category: SearchCategory,
        val items: List<ItemResponse>,
        val hasMore: Boolean,
        val totalCount: Long,
    ) {
        companion object {
            fun from(section: SearchResult.SectionPreview): SectionResponse = SectionResponse(
                category = section.category,
                items = section.items.map(ItemResponse::from),
                hasMore = section.hasMore,
                totalCount = section.totalCount,
            )
        }
    }

    data class SectionsResponse(
        val sections: List<SectionResponse>,
    ) {
        companion object {
            fun from(result: SearchResult.Sections): SectionsResponse = SectionsResponse(
                sections = result.sections.map(SectionResponse::from),
            )
        }
    }
}
