package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CropCatalogService(
    private val cropRepository: CropRepository
) {
    fun listCrops(): List<CropResult.CropSummary> {
        return cropRepository.findAllByOrderByNameAscExternalNoAsc()
            .map(CropResult.CropSummary::from)
    }

    fun listCropsByCategory(category: CropUsePartCategory): List<CropResult.CropSummary> {
        return cropRepository.findAllByUsePartCategoryOrderByNameAscExternalNoAsc(category)
            .map(CropResult.CropSummary::from)
    }

    fun listCategories(): List<CropResult.CategorySummary> {
        return CropUsePartCategory.entries.map(CropResult.CategorySummary::from)
    }
}
