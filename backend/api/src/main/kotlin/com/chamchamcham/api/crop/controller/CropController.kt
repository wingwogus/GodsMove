package com.chamchamcham.api.crop.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.crop.dto.CropResponses
import com.chamchamcham.application.crop.CropCatalogService
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/crops")
class CropController(
    private val cropCatalogService: CropCatalogService
) {
    @GetMapping
    fun listCrops(): ResponseEntity<ApiResponse<List<CropResponses.CropResponse>>> {
        val crops = cropCatalogService.listCrops().map(CropResponses.CropResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(crops))
    }

    @GetMapping("/categories")
    fun listCategories(): ResponseEntity<ApiResponse<List<CropResponses.CategoryResponse>>> {
        val categories = cropCatalogService.listCategories().map(CropResponses.CategoryResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(categories))
    }

    @GetMapping("/categories/{category}/crops")
    fun listCropsByCategory(
        @PathVariable category: CropUsePartCategory
    ): ResponseEntity<ApiResponse<List<CropResponses.CropResponse>>> {
        val crops = cropCatalogService.listCropsByCategory(category).map(CropResponses.CropResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(crops))
    }
}
