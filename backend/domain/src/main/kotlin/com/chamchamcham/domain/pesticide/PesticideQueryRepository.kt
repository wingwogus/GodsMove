package com.chamchamcham.domain.pesticide

import java.util.UUID

interface PesticideQueryRepository {
    fun search(condition: SearchCondition): List<Pesticide>

    /** 작물명(PSIS crop_name 정확 일치)에 등록된 농약×병해충 행을 조회한다. */
    fun findByCropNames(cropNames: List<String>, maxRows: Int): List<VoiceCatalogRow>

    data class VoiceCatalogRow(
        val itemName: String,
        val brandName: String,
        val pestName: String,
    )

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
