package com.chamchamcham.application.policy.recommendation

import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
        assertThat(scored.eligible).isTrue()
        assertThat(scored.reason).isEqualTo("청년농 대상이고 재배 품목이 맞아요.")
    }

    @Test
    fun `returns zero score when a required target group does not match`() {
        val profile = PolicyMemberProfile(
            birthDate = LocalDate.of(1975, 1, 1),
            experienceLevel = 20,
            managementType = ManagementType.NON_REGISTERED_FARMER,
            cropNames = setOf("참당귀"),
            cropUsePartCategories = setOf("ROOT_BARK"),
            farmRegionTokens = setOf("충청북도", "제천시")
        )

        val scored = scorer.score(
            profile = profile,
            policyTargetTags = setOf("YOUNG_FARMER"),
            policyCropTags = setOf("MEDICINAL_CROP"),
            policyRegionTags = setOf("충청북도"),
            onlineApplyAvailable = true,
            today = LocalDate.of(2026, 4, 1)
        )

        assertThat(scored.score).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(scored.eligible).isFalse()
    }

    @Test
    fun `returns zero score when a required region group does not match`() {
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
            policyTargetTags = setOf("YOUNG_FARMER"),
            policyCropTags = setOf("MEDICINAL_CROP"),
            policyRegionTags = setOf("전라남도"),
            onlineApplyAvailable = true,
            today = LocalDate.of(2026, 4, 1)
        )

        assertThat(scored.score).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(scored.eligible).isFalse()
    }

    @Test
    fun `returns zero score when no meaningful matching group exists`() {
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
            policyTargetTags = emptySet(),
            policyCropTags = emptySet(),
            policyRegionTags = setOf("전국"),
            onlineApplyAvailable = true,
            today = LocalDate.of(2026, 4, 1)
        )

        assertThat(scored.score).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(scored.eligible).isFalse()
    }
}
