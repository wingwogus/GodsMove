package com.chamchamcham.api.policy.dto.recommendation

import com.chamchamcham.application.policy.recommendation.PolicyRecommendationResult
import java.math.BigDecimal
import java.util.UUID

data class PolicyRecommendationPageResponse(
    val items: List<PolicyRecommendationItemResponse>,
    val nextCursor: String?
) {
    companion object {
        fun from(result: PolicyRecommendationResult.Page): PolicyRecommendationPageResponse =
            PolicyRecommendationPageResponse(
                items = result.items.map(PolicyRecommendationItemResponse::from),
                nextCursor = result.nextCursor
            )
    }
}

data class PolicyRecommendationItemResponse(
    val recommendationId: UUID,
    val policyProgramId: UUID,
    val programTitle: String,
    val eligibilitySummary: String,
    val benefitSummary: String,
    val applicationPeriodLabel: String,
    val agencyName: String,
    val score: BigDecimal,
    val reason: String
) {
    companion object {
        fun from(result: PolicyRecommendationResult.Card): PolicyRecommendationItemResponse =
            PolicyRecommendationItemResponse(
                recommendationId = result.recommendationId,
                policyProgramId = result.policyProgramId,
                programTitle = result.programTitle,
                eligibilitySummary = result.eligibilitySummary,
                benefitSummary = result.benefitSummary,
                applicationPeriodLabel = result.applicationPeriodLabel,
                agencyName = result.agencyName,
                score = result.score,
                reason = result.reason
            )
    }
}
