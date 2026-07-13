package com.chamchamcham.api.pesticide.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.pesticide.dto.PesticideResponses.PestSummaryResponse
import com.chamchamcham.api.pesticide.dto.PesticideResponses.PesticidePageResponse
import com.chamchamcham.application.pesticide.PesticideCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PesticideController(
    private val pesticideCatalogService: PesticideCatalogService
) {
    @GetMapping("/pesticides")
    fun searchPesticides(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PesticidePageResponse>> {
        val result = pesticideCatalogService.search(keyword, cursor, size)
        return ResponseEntity.ok(ApiResponse.ok(PesticidePageResponse.from(result)))
    }

    @GetMapping("/pesticides/{pesticideId}/pests")
    fun listPests(
        @PathVariable pesticideId: UUID,
    ): ResponseEntity<ApiResponse<List<PestSummaryResponse>>> {
        val result = pesticideCatalogService.listPestsByPesticide(pesticideId)
        return ResponseEntity.ok(ApiResponse.ok(result.map(PestSummaryResponse::from)))
    }
}
