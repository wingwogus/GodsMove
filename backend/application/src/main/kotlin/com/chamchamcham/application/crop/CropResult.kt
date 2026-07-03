package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import java.util.UUID

object CropResult {
    data class CropSummary(
        val id: UUID,
        val externalNo: Int,
        val name: String,
        val usePartCategory: String,
        val usePartCategoryLabel: String
    ) {
        companion object {
            fun from(crop: Crop): CropSummary {
                return CropSummary(
                    id = requireNotNull(crop.id) { "Persisted crop id is required" },
                    externalNo = crop.externalNo,
                    name = crop.name,
                    usePartCategory = crop.usePartCategory.name,
                    usePartCategoryLabel = crop.usePartCategory.label
                )
            }
        }
    }

    data class CategorySummary(
        val code: String,
        val label: String
    ) {
        companion object {
            fun from(category: CropUsePartCategory): CategorySummary {
                return CategorySummary(code = category.name, label = category.label)
            }
        }
    }
}
