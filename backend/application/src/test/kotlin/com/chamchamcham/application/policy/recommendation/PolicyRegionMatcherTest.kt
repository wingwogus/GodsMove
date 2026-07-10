package com.chamchamcham.application.policy.recommendation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolicyRegionMatcherTest {
    private val matcher = PolicyRegionMatcher()

    @Test
    fun `extracts province and city tokens from farm addresses`() {
        val tokens = matcher.extractRegionTokens(
            listOf("충청북도 제천시 봉양읍 의암로 1", "충북 제천시 봉양읍 1")
        )

        assertThat(tokens).contains("충청북도", "제천시")
    }

    @Test
    fun `national policy matches any farm region`() {
        assertThat(matcher.matches(setOf("충청북도", "제천시"), setOf("전국"))).isTrue()
    }
}
