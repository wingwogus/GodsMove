package com.chamchamcham.api.report.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.report.dto.FarmingWorkReportResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingWorkReportQueryService
import com.chamchamcham.application.report.FarmingWorkReportSearchCondition
import com.chamchamcham.domain.farming.WorkType
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
class FarmingWorkReportController(
    private val queryService: FarmingWorkReportQueryService,
) {
    @GetMapping("/work-items")
    fun list(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) farmId: Set<UUID>?,
        @RequestParam(required = false) cropId: Set<UUID>?,
        @RequestParam(required = false) workType: WorkType?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<ApiResponse<FarmingWorkReportResponses.PageResponse>> {
        val result = try {
            queryService.list(
                FarmingWorkReportSearchCondition(
                    memberId = parseMemberId(memberId),
                    farmIds = farmId.orEmpty(),
                    cropIds = cropId.orEmpty(),
                    workType = workType,
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
        return ResponseEntity.ok(ApiResponse.ok(FarmingWorkReportResponses.PageResponse.from(result)))
    }

    @GetMapping("/{reportId}/work-types/{workType}")
    fun getDetail(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable reportId: UUID,
        @PathVariable workType: WorkType,
    ): ResponseEntity<ApiResponse<FarmingWorkReportResponses.DetailResponse>> {
        val result = queryService.getDetail(parseMemberId(memberId), reportId, workType)
        return ResponseEntity.ok(ApiResponse.ok(FarmingWorkReportResponses.DetailResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return try {
            UUID.fromString(memberId)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
