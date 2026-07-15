package com.chamchamcham.api.coaching.chat.controller

import com.chamchamcham.api.coaching.chat.dto.CoachingRagRequests
import com.chamchamcham.api.coaching.chat.dto.CoachingRagResponses
import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.application.coaching.chat.CoachingRagService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/coaching/rag")
class CoachingRagController(
    private val coachingRagService: CoachingRagService
) {
    @PostMapping("/query")
    fun query(
        principal: Principal?,
        @Valid @RequestBody request: CoachingRagRequests.QueryRequest
    ): ResponseEntity<ApiResponse<CoachingRagResponses.QueryResponse>> {
        val memberId = principal?.name
            ?: SecurityContextHolder.getContext().authentication?.name
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val result = coachingRagService.answer(request.toCommand(parseMemberId(memberId)))
        return ResponseEntity.ok(ApiResponse.ok(CoachingRagResponses.QueryResponse.from(result)))
    }

    private fun parseMemberId(memberId: String): UUID {
        return try {
            UUID.fromString(memberId)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
