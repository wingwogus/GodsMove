package com.chamchamcham.api.report.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.report.dto.FarmingCycleReportResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingCycleReportQueryService
import com.chamchamcham.application.report.FarmingCycleReportSearchCondition
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Validated
@RequestMapping("/api/v1/farming-reports")
class FarmingCycleReportController(
    private val queryService: FarmingCycleReportQueryService,
) {
    @GetMapping
    fun listCompleted(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) farmId: UUID?,
        @RequestParam(required = false) cropId: UUID?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<ApiResponse<FarmingCycleReportResponses.PageResponse>> {
        val result = try {
            queryService.listCompleted(
                FarmingCycleReportSearchCondition(
                    memberId = parseMemberId(memberId),
                    farmId = farmId,
                    cropId = cropId,
                    cursor = cursor,
                    size = size,
                ),
            )
        } catch (exception: BusinessException) {
            if (exception.errorCode == ErrorCode.INVALID_CURSOR) {
                throw BusinessException(ErrorCode.INVALID_INPUT)
            }
            throw exception
        }
        return ResponseEntity.ok(
            ApiResponse.ok(FarmingCycleReportResponses.PageResponse.from(result)),
        )
    }

    @GetMapping("/{reportId}")
    fun getDetail(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable reportId: UUID,
    ): ResponseEntity<ApiResponse<FarmingCycleReportResponses.DetailResponse>> {
        val result = queryService.getDetail(parseMemberId(memberId), reportId)
        return ResponseEntity.ok(
            ApiResponse.ok(FarmingCycleReportResponses.DetailResponse.from(result)),
        )
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
