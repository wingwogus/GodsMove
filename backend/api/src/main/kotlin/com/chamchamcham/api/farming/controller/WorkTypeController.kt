package com.chamchamcham.api.farming.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.farming.dto.WorkTypeResponses
import com.chamchamcham.application.farming.WorkTypeCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/work-types")
class WorkTypeController(
    private val workTypeCatalogService: WorkTypeCatalogService
) {
    @GetMapping
    fun listWorkTypes(): ResponseEntity<ApiResponse<List<WorkTypeResponses.WorkTypeResponse>>> {
        val workTypes = workTypeCatalogService.listWorkTypes().map(WorkTypeResponses.WorkTypeResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(workTypes))
    }
}
