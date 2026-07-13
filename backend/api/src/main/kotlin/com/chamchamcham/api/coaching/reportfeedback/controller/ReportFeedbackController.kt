package com.chamchamcham.api.coaching.reportfeedback.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.coaching.reportfeedback.dto.ReportFeedbackResponses
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/farming-reports/{reportId}/feedback")
class ReportFeedbackController(
    private val queryService: ReportFeedbackQueryService,
) {
    @GetMapping
    fun getStatus(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable reportId: UUID,
    ): ResponseEntity<ApiResponse<ReportFeedbackResponses.StatusResponse>> {
        val result = queryService.get(parseMemberId(memberId), reportId)
        return ResponseEntity.ok(ApiResponse.ok(ReportFeedbackResponses.StatusResponse.from(result)))
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
