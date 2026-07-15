package com.chamchamcham.application.search

import java.time.LocalDateTime
import java.util.UUID

object SearchResult {
    data class Item(
        val category: SearchCategory,
        val id: UUID,
        val title: String,
        val snippet: String,
        val thumbnailUrl: String?,
        val createdAt: LocalDateTime,
    )

    data class Page(
        val items: List<Item>,
        val nextCursor: String?,
        val totalCount: Long,
    )

    data class SectionPreview(
        val category: SearchCategory,
        val items: List<Item>,
        val hasMore: Boolean,
        val totalCount: Long,
    )

    data class Sections(
        val sections: List<SectionPreview>,
    )
}
