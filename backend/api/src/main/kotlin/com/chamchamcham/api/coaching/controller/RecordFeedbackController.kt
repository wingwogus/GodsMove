package com.chamchamcham.api.coaching.controller

import com.chamchamcham.api.coaching.dto.RecordFeedbackResponses
import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackQueryService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/farming-records/{recordId}/feedback")
class RecordFeedbackController(
    private val queryService: RecordFeedbackQueryService,
) {
    @GetMapping
    fun getStatus(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable recordId: UUID,
    ): ResponseEntity<ApiResponse<RecordFeedbackResponses.StatusResponse>> {
        val result = queryService.get(parseMemberId(memberId), recordId)
        return ResponseEntity.ok(ApiResponse.ok(RecordFeedbackResponses.StatusResponse.from(result)))
    }

    @PostMapping("/regenerate")
    fun regenerate(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable recordId: UUID,
    ): ResponseEntity<ApiResponse<RecordFeedbackResponses.StatusResponse>> {
        val result = queryService.regenerate(parseMemberId(memberId), recordId)
        return ResponseEntity.ok(ApiResponse.ok(RecordFeedbackResponses.StatusResponse.from(result)))
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
