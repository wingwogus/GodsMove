package com.chamchamcham.application.search

import java.util.UUID

data class SearchQuery(
    val memberId: UUID,
    val keyword: String?,
    val cursor: String?,
    val size: Int,
)
