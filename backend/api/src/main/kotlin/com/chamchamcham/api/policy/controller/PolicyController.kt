package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.policy.dto.program.PolicyProgramDetailResponse
import com.chamchamcham.api.policy.dto.recommendation.PolicyRecommendationPageResponse
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.recommendation.PolicyRecommendationService
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
import com.chamchamcham.domain.policy.PolicyRecommendationSort
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
@RequestMapping("/api/v1/policies")
class PolicyController(
    private val policyRecommendationService: PolicyRecommendationService
) {
    @GetMapping("/recommendations")
    fun listRecommendations(
        @AuthenticationPrincipal principal: Any?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) benefitCategory: String?,
        @RequestParam(defaultValue = "RECOMMENDED") sort: String
    ): ResponseEntity<ApiResponse<PolicyRecommendationPageResponse>> {
        val parsedBenefitCategory = benefitCategory?.let {
            PolicyBenefitCategory.fromKey(it) ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val parsedSort = PolicyRecommendationSort.fromKey(sort)
            ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        val result = policyRecommendationService.listRecommendations(
            memberId = parseMemberId(principal),
            cursor = cursor,
            size = size,
            benefitCategory = parsedBenefitCategory,
            sort = parsedSort
        )
        return ResponseEntity.ok(ApiResponse.ok(PolicyRecommendationPageResponse.from(result)))
    }

    @GetMapping("/{policyProgramId}")
    fun getProgramDetail(
        @AuthenticationPrincipal principal: Any?,
        @PathVariable policyProgramId: UUID
    ): ResponseEntity<ApiResponse<PolicyProgramDetailResponse>> {
        val result = policyRecommendationService.getProgramDetail(parseMemberId(principal), policyProgramId)
        return ResponseEntity.ok(ApiResponse.ok(PolicyProgramDetailResponse.from(result)))
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
