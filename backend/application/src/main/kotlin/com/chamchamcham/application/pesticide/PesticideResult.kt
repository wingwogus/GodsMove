package com.chamchamcham.application.pesticide

import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.Pesticide
import java.util.UUID

object PesticideResult {
    data class Summary(
        val id: UUID,
        val itemName: String,
        val brandName: String,
        val usageCategory: String?,
        val manufacturer: String?,
    ) {
        companion object {
            fun from(pesticide: Pesticide): Summary = Summary(
                id = requireNotNull(pesticide.id) { "Persisted pesticide id is required" },
                itemName = pesticide.itemName,
                brandName = pesticide.brandName,
                usageCategory = pesticide.usageCategory,
                manufacturer = pesticide.manufacturer,
            )
        }
    }

    data class Page(
        val items: List<Summary>,
        val nextCursor: String?,
    )

    data class PestSummary(
        val id: UUID,
        val name: String,
    ) {
        companion object {
            fun from(pest: Pest): PestSummary = PestSummary(
                id = requireNotNull(pest.id) { "Persisted pest id is required" },
                name = pest.name,
            )
        }
    }
}
