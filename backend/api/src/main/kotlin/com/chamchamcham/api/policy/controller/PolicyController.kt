package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.policy.dto.PolicyResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.PolicyRecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PolicyController(
    private val policyRecommendationService: PolicyRecommendationService
) {
    @GetMapping("/policy-recommendations")
    fun listRecommendations(
        @AuthenticationPrincipal principal: Any?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PolicyResponses.RecommendationPageResponse>> {
        val result = policyRecommendationService.listRecommendations(parseMemberId(principal), cursor, size)
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.RecommendationPageResponse.from(result)))
    }

    @GetMapping("/policy-programs/{policyProgramId}")
    fun getProgramDetail(
        @AuthenticationPrincipal principal: Any?,
        @PathVariable policyProgramId: UUID
    ): ResponseEntity<ApiResponse<PolicyResponses.PolicyDetailResponse>> {
        val result = policyRecommendationService.getProgramDetail(parseMemberId(principal), policyProgramId)
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.PolicyDetailResponse.from(result)))
    }

    private fun parseMemberId(principal: Any?): UUID {
        val memberId = when (principal) {
            is String -> principal
            is UserDetails -> principal.username
            else -> null
        }
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return runCatching { UUID.fromString(memberId) }
            .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }
    }
}
