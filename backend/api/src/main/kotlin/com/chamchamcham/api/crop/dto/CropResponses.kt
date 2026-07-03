package com.chamchamcham.api.crop.dto

import com.chamchamcham.application.crop.CropResult
import java.util.UUID

object CropResponses {
    data class CropResponse(
        val id: UUID,
        val externalNo: Int,
        val name: String,
        val usePartCategory: String,
        val usePartCategoryLabel: String
    ) {
        companion object {
            fun from(result: CropResult.CropSummary): CropResponse {
                return CropResponse(
                    id = result.id,
                    externalNo = result.externalNo,
                    name = result.name,
                    usePartCategory = result.usePartCategory,
                    usePartCategoryLabel = result.usePartCategoryLabel
                )
            }
        }
    }

    data class CategoryResponse(
        val code: String,
        val label: String
    ) {
        companion object {
            fun from(result: CropResult.CategorySummary): CategoryResponse {
                return CategoryResponse(code = result.code, label = result.label)
            }
        }
    }
}
