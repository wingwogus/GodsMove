package com.chamchamcham.application.search

import com.chamchamcham.application.policy.recommendation.PolicyRecommendationResult
import com.chamchamcham.application.policy.recommendation.PolicyRecommendationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PolicyRecommendationSearcherTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recommendationId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val policyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val createdAt = LocalDateTime.of(2026, 6, 1, 9, 0)

    @Mock private lateinit var policyRecommendationService: PolicyRecommendationService

    private lateinit var searcher: PolicyRecommendationSearcher

    @BeforeEach
    fun setUp() {
        searcher = PolicyRecommendationSearcher(policyRecommendationService)
    }

    @Test
    fun `category returns POLICY`() {
        assertThat(searcher.category()).isEqualTo(SearchCategory.POLICY)
    }

    @Test
    fun `search delegates to policyRecommendationService and normalizes items`() {
        `when`(
            policyRecommendationService.searchRecommendations(
                memberId,
                "황기",
                "cursor-1",
                10
            )
        ).thenReturn(
            PolicyRecommendationResult.Page(
                items = listOf(
                    PolicyRecommendationResult.Card(
                        recommendationId = recommendationId,
                        policyProgramId = policyProgramId,
                        programTitle = "황기 재배 지원사업",
                        eligibilitySummary = "농업경영체 등록 농가",
                        benefitSummary = "최대 500만원 지원",
                        applicationPeriodLabel = "2026.03.01 ~ 2026.03.31",
                        agencyName = "농림축산식품부",
                        score = BigDecimal("87.5"),
                        reason = "재배 작물 일치",
                        createdAt = createdAt,
                    )
                ),
                nextCursor = "cursor-2"
            )
        )
        `when`(policyRecommendationService.countSearchRecommendations(memberId, "황기")).thenReturn(9L)

        val page = searcher.search(
            SearchQuery(memberId = memberId, keyword = "황기", cursor = "cursor-1", size = 10)
        )

        assertThat(page.nextCursor).isEqualTo("cursor-2")
        assertThat(page.totalCount).isEqualTo(9L)
        val item = page.items.single()
        assertThat(item.category).isEqualTo(SearchCategory.POLICY)
        assertThat(item.id).isEqualTo(policyProgramId)
        assertThat(item.title).isEqualTo("황기 재배 지원사업")
        assertThat(item.snippet).isEqualTo("최대 500만원 지원")
        assertThat(item.thumbnailUrl).isNull()
        assertThat(item.createdAt).isEqualTo(createdAt)
    }
}
