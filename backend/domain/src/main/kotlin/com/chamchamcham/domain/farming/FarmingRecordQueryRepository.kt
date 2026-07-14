package com.chamchamcham.domain.farming

import com.chamchamcham.domain.crop.CropUsePartCategory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

interface FarmingRecordQueryRepository {
    fun search(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val cropIds: List<UUID> = emptyList(),
        val workTypes: List<WorkType> = emptyList(),
        val workedAtFrom: LocalDateTime?,
        val workedAtTo: LocalDateTime?,
        val keyword: String? = null,
        val matchedWorkTypes: List<WorkType> = emptyList(),
        val matchedParts: List<CropUsePartCategory> = emptyList(),
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val workedAt: LocalDateTime,
        val id: UUID
    )

    data class Row(
        val record: FarmingRecord,
        val thumbnailUrl: String?,
        val irrigationMethod: IrrigationMethod? = null,
        val harvestAmount: BigDecimal? = null,
        val pesticideName: String? = null,
        val weedingMethod: WeedingMethod? = null,
    )

    data class SearchResult(
        val rows: List<Row>
    )
}
