package com.chamchamcham.application.policy

import com.chamchamcham.domain.member.ManagementType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period

data class PolicyMemberProfile(
    val birthDate: LocalDate?,
    val experienceLevel: Int?,
    val managementType: ManagementType?,
    val cropNames: Set<String>,
    val cropUsePartCategories: Set<String>,
    val farmRegionTokens: Set<String>
)

data class PolicyScore(
    val score: BigDecimal,
    val reason: String
)

@Component
class PolicyRecommendationScorer(
    private val regionMatcher: PolicyRegionMatcher
) {
    fun score(
        profile: PolicyMemberProfile,
        policyTargetTags: Set<String>,
        policyCropTags: Set<String>,
        policyRegionTags: Set<String>,
        onlineApplyAvailable: Boolean,
        today: LocalDate
    ): PolicyScore {
        var score = 30
        val signals = mutableListOf<String>()

        if (policyTargetTags.contains("YOUNG_FARMER") && profile.birthDate?.let { Period.between(it, today).years < 40 } == true) {
            score += 20
            signals += "청년농 대상"
        }
        if (policyTargetTags.contains("REGISTERED_FARMER") && profile.managementType != ManagementType.NON_REGISTERED_FARMER) {
            score += 15
            signals += "경영체 유형"
        }
        if (policyTargetTags.contains("AGRICULTURAL_CORPORATION") && profile.managementType == ManagementType.AGRICULTURAL_CORPORATION) {
            score += 15
            signals += "법인 유형"
        }
        if (policyTargetTags.contains("RETURNING_FARMER") && (profile.experienceLevel ?: 99) <= 3) {
            score += 12
            signals += "영농경력"
        }
        if (policyCropTags.contains("MEDICINAL_CROP") && profile.cropUsePartCategories.contains("ROOT_BARK")) {
            score += 18
            signals += "재배 품목"
        }
        if (policyCropTags.contains("SPECIAL_CROP") && profile.cropNames.any { it.contains("작물") || it.contains("당귀") || it.contains("작약") }) {
            score += 10
            signals += "작물 분야"
        }
        if (regionMatcher.matches(profile.farmRegionTokens, policyRegionTags)) {
            score += 10
            signals += "농장 지역"
        }
        if (onlineApplyAvailable) {
            score += 3
            signals += "온라인 신청"
        }

        val reason = when {
            signals.contains("청년농 대상") && signals.contains("재배 품목") -> "청년농 대상이고 재배 품목이 맞아요."
            signals.contains("경영체 유형") && signals.contains("재배 품목") -> "경영체 유형과 재배 품목이 맞아요."
            signals.contains("농장 지역") -> "신청 가능한 정책이고 농장 지역이 맞아요."
            signals.isNotEmpty() -> "${signals.take(2).joinToString("과 ")}이 맞아요."
            else -> "신청 가능한 정책이에요."
        }

        return PolicyScore(BigDecimal.valueOf(score.toLong()), reason)
    }
}
