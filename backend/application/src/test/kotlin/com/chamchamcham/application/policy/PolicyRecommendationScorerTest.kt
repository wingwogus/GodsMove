package com.chamchamcham.application.policy

import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PolicyRecommendationScorerTest {
    private val scorer = PolicyRecommendationScorer(PolicyRegionMatcher())

    @Test
    fun `scores young farmer medicinal crop and region matches above generic policy`() {
        val profile = PolicyMemberProfile(
            birthDate = LocalDate.of(1995, 1, 1),
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            cropNames = setOf("참당귀"),
            cropUsePartCategories = setOf("ROOT_BARK"),
            farmRegionTokens = setOf("충청북도", "제천시")
        )

        val scored = scorer.score(
            profile = profile,
            policyTargetTags = setOf("YOUNG_FARMER", "REGISTERED_FARMER"),
            policyCropTags = setOf("MEDICINAL_CROP"),
            policyRegionTags = setOf("충청북도"),
            onlineApplyAvailable = true,
            today = LocalDate.of(2026, 4, 1)
        )

        assertThat(scored.score.toDouble()).isGreaterThanOrEqualTo(70.0)
        assertThat(scored.reason).isEqualTo("청년농 대상이고 재배 품목이 맞아요.")
    }
}
