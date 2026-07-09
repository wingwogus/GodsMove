package com.chamchamcham.application.policy.recommendation

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
    val eligible: Boolean,
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
        if (!isEligible(profile, policyTargetTags, policyCropTags, policyRegionTags, today)) {
            return PolicyScore(
                eligible = false,
                score = BigDecimal.ZERO,
                reason = "추천 조건이 맞지 않아요."
            )
        }

        var score = 30
        val signals = mutableListOf<String>()

        if (policyTargetTags.contains("YOUNG_FARMER") && matchesYoungFarmer(profile, today)) {
            score += 20
            signals += "청년농 대상"
        }
        if (policyTargetTags.contains("REGISTERED_FARMER") && matchesRegisteredFarmer(profile)) {
            score += 15
            signals += "경영체 유형"
        }
        if (policyTargetTags.contains("AGRICULTURAL_CORPORATION") && matchesAgriculturalCorporation(profile)) {
            score += 15
            signals += "법인 유형"
        }
        if (policyTargetTags.contains("RETURNING_FARMER") && matchesReturningFarmer(profile)) {
            score += 12
            signals += "영농경력"
        }
        if (policyCropTags.contains("MEDICINAL_CROP") && matchesMedicinalCrop(profile)) {
            score += 18
            signals += "재배 품목"
        }
        if (policyCropTags.contains("SPECIAL_CROP") && matchesSpecialCrop(profile)) {
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

        return PolicyScore(
            eligible = true,
            score = BigDecimal.valueOf(score.toLong()),
            reason = reason
        )
    }

    private fun isEligible(
        profile: PolicyMemberProfile,
        policyTargetTags: Set<String>,
        policyCropTags: Set<String>,
        policyRegionTags: Set<String>,
        today: LocalDate
    ): Boolean {
        val targetRequirements = policyTargetTags.intersect(matchableTargetTags)
        val cropRequirements = policyCropTags.intersect(matchableCropTags)
        val hasSpecificRegionRequirement = policyRegionTags.any { it != NATIONAL_REGION_TAG }
        val hasMeaningfulRequirement =
            targetRequirements.isNotEmpty() || cropRequirements.isNotEmpty() || hasSpecificRegionRequirement

        if (!hasMeaningfulRequirement) {
            return false
        }
        if (targetRequirements.isNotEmpty() && targetRequirements.none { matchesTargetTag(profile, it, today) }) {
            return false
        }
        if (cropRequirements.isNotEmpty() && cropRequirements.none { matchesCropTag(profile, it) }) {
            return false
        }
        if (hasSpecificRegionRequirement && !regionMatcher.matches(profile.farmRegionTokens, policyRegionTags)) {
            return false
        }
        return true
    }

    private fun matchesTargetTag(profile: PolicyMemberProfile, tag: String, today: LocalDate): Boolean =
        when (tag) {
            "YOUNG_FARMER" -> matchesYoungFarmer(profile, today)
            "REGISTERED_FARMER" -> matchesRegisteredFarmer(profile)
            "AGRICULTURAL_CORPORATION" -> matchesAgriculturalCorporation(profile)
            "RETURNING_FARMER" -> matchesReturningFarmer(profile)
            else -> false
        }

    private fun matchesCropTag(profile: PolicyMemberProfile, tag: String): Boolean =
        when (tag) {
            "MEDICINAL_CROP" -> matchesMedicinalCrop(profile)
            "SPECIAL_CROP" -> matchesSpecialCrop(profile)
            else -> false
        }

    private fun matchesYoungFarmer(profile: PolicyMemberProfile, today: LocalDate): Boolean =
        profile.birthDate?.let { Period.between(it, today).years < 40 } == true

    private fun matchesRegisteredFarmer(profile: PolicyMemberProfile): Boolean =
        profile.managementType != null && profile.managementType != ManagementType.NON_REGISTERED_FARMER

    private fun matchesAgriculturalCorporation(profile: PolicyMemberProfile): Boolean =
        profile.managementType == ManagementType.AGRICULTURAL_CORPORATION

    private fun matchesReturningFarmer(profile: PolicyMemberProfile): Boolean =
        (profile.experienceLevel ?: 99) <= 3

    private fun matchesMedicinalCrop(profile: PolicyMemberProfile): Boolean =
        profile.cropUsePartCategories.contains("ROOT_BARK")

    private fun matchesSpecialCrop(profile: PolicyMemberProfile): Boolean =
        profile.cropNames.any { it.contains("작물") || it.contains("당귀") || it.contains("작약") }

    private companion object {
        const val NATIONAL_REGION_TAG = "전국"
        val matchableTargetTags = setOf(
            "YOUNG_FARMER",
            "REGISTERED_FARMER",
            "AGRICULTURAL_CORPORATION",
            "RETURNING_FARMER"
        )
        val matchableCropTags = setOf(
            "MEDICINAL_CROP",
            "SPECIAL_CROP"
        )
    }
}
