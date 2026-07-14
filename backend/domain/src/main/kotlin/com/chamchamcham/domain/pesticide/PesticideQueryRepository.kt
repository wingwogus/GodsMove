package com.chamchamcham.domain.pesticide

import java.util.UUID

interface PesticideQueryRepository {
    fun search(condition: SearchCondition): List<Pesticide>

    data class Cursor(
        val brandName: String,
        val id: UUID,
    )

    data class SearchCondition(
        val keyword: String?,
        val cursor: Cursor?,
        val size: Int,
    )
}
