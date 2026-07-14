package com.chamchamcham.application.search

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideQueryRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest

@ExtendWith(MockitoExtension::class)
class SearchSuggestionServiceTest {
    @Mock private lateinit var cropRepository: CropRepository

    @Mock private lateinit var pesticideQueryRepository: PesticideQueryRepository

    @Mock private lateinit var policyProgramRepository: PolicyProgramRepository

    private lateinit var service: SearchSuggestionService

    @BeforeEach
    fun setUp() {
        service = SearchSuggestionService(cropRepository, pesticideQueryRepository, policyProgramRepository)
    }

    @Test
    fun `null keyword returns empty list`() {
        assertThat(service.suggest(null).keywords).isEmpty()
    }

    @Test
    fun `blank keyword returns empty list`() {
        assertThat(service.suggest("   ").keywords).isEmpty()
    }

    @Test
    fun `trimmed original keyword is always first and sources merge in round robin order`() {
        stubSources(
            keyword = "황기",
            crops = listOf("작물1", "작물2"),
            pesticides = listOf("약제1", "약제2"),
            titles = listOf("정책1", "정책2"),
            agencies = listOf("기관1", "기관2"),
        )

        val result = service.suggest("  황기  ").keywords

        assertThat(result).containsExactly(
            "황기",
            "작물1", "약제1", "정책1", "기관1",
            "작물2", "약제2", "정책2", "기관2",
        )
    }

    @Test
    fun `single non-empty source fills the remaining slots`() {
        stubSources(
            keyword = "작물",
            crops = (1..9).map { "작물$it" },
            pesticides = emptyList(),
            titles = emptyList(),
            agencies = emptyList(),
        )

        val result = service.suggest("작물").keywords

        assertThat(result).hasSize(10)
        assertThat(result.first()).isEqualTo("작물")
        assertThat(result.drop(1)).containsExactly(
            "작물1", "작물2", "작물3", "작물4", "작물5", "작물6", "작물7", "작물8", "작물9",
        )
    }

    @Test
    fun `returns only available candidates when total is under nine`() {
        stubSources(
            keyword = "인삼",
            crops = listOf("인삼밭"),
            pesticides = listOf("인삼약"),
            titles = emptyList(),
            agencies = emptyList(),
        )

        val result = service.suggest("인삼").keywords

        assertThat(result).containsExactly("인삼", "인삼밭", "인삼약")
    }

    @Test
    fun `deduplicates candidates ignoring case and whitespace keeping first occurrence`() {
        stubSources(
            keyword = "과일",
            crops = listOf("Apple"),
            pesticides = listOf(" apple "),
            titles = listOf("APPLE"),
            agencies = listOf("Banana"),
        )

        val result = service.suggest("과일").keywords

        assertThat(result).containsExactly("과일", "Apple", "Banana")
    }

    @Test
    fun `excludes candidates equal to original keyword from related suggestions`() {
        stubSources(
            keyword = "사과",
            crops = listOf(" 사과 "),
            pesticides = listOf("사과약"),
            titles = emptyList(),
            agencies = emptyList(),
        )

        val result = service.suggest("사과").keywords

        assertThat(result).containsExactly("사과", "사과약")
    }

    @Test
    fun `total keywords never exceed ten`() {
        stubSources(
            keyword = "농사",
            crops = (1..9).map { "C$it" },
            pesticides = (1..9).map { "P$it" },
            titles = (1..9).map { "T$it" },
            agencies = (1..9).map { "A$it" },
        )

        val result = service.suggest("농사").keywords

        assertThat(result).hasSize(10)
        assertThat(result.first()).isEqualTo("농사")
    }

    private fun stubSources(
        keyword: String,
        crops: List<String>,
        pesticides: List<String>,
        titles: List<String>,
        agencies: List<String>,
    ) {
        `when`(cropRepository.findTop9ByNameContainingIgnoreCaseOrderByNameAsc(keyword))
            .thenReturn(crops.map { Crop(externalNo = 0, name = it, usePartCategory = CropUsePartCategory.ROOT_BARK) })
        `when`(
            pesticideQueryRepository.search(
                PesticideQueryRepository.SearchCondition(keyword = keyword, cursor = null, size = 9)
            )
        ).thenReturn(pesticides.map { Pesticide(itemName = it, brandName = it) })
        `when`(policyProgramRepository.findTitleSuggestions(keyword, PageRequest.of(0, 9)))
            .thenReturn(titles.map { policyProgram(title = it, agencyName = "기관") })
        `when`(policyProgramRepository.findAgencySuggestions(keyword, PageRequest.of(0, 9)))
            .thenReturn(agencies.map { policyProgram(title = "정책", agencyName = it) })
    }

    private fun policyProgram(title: String, agencyName: String): PolicyProgram =
        PolicyProgram(
            title = title,
            body = "정책 상세",
            region = "전국",
            targetManagementType = null,
            agencyName = agencyName,
        )
}
