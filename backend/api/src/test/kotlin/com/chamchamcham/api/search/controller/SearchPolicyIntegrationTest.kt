package com.chamchamcham.api.search.controller

import com.chamchamcham.api.testsupport.signedTestToken
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.policy.PolicyProgram
import com.chamchamcham.domain.policy.PolicyProgramRepository
import com.chamchamcham.domain.policy.PolicySource
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchPolicyIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val policyProgramRepository: PolicyProgramRepository,
) {
    private lateinit var accessToken: String
    private lateinit var programAId: String

    @BeforeEach
    fun setUp() {
        val member = memberRepository.save(Member(email = "policy-search-member@example.com", passwordHash = null))
        accessToken = signedTestToken(subject = member.id.toString(), tokenType = "access", role = "ROLE_USER")

        val programA = policyProgramRepository.save(
            buildProgram(
                title = "청년 농업인 지원사업",
                summary = "청년 농업인 정착 지원",
                benefitSummary = "월 100만원 지원",
                externalId = "policy-search-a",
                sourceUrl = "https://policy.example.com/a"
            )
        )
        policyProgramRepository.save(
            buildProgram(
                title = "스마트팜 구축 지원",
                summary = "스마트팜 시설 구축 보조",
                benefitSummary = "시설비 50% 지원",
                externalId = "policy-search-b",
                sourceUrl = "https://policy.example.com/b"
            )
        )
        policyProgramRepository.save(
            buildProgram(
                title = "청년 미동기화 정책",
                summary = "상세 수집 실패 건",
                benefitSummary = "지원 확인",
                externalId = "policy-search-c",
                sourceUrl = null,
                detailSynced = false
            )
        )

        programAId = requireNotNull(programA.id).toString()
    }

    @Test
    fun `policy search with matching keyword returns only the synced matching program with card fields`() {
        mockMvc.perform(
            get("/api/v1/search/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("keyword", "청년")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
            .andExpect(jsonPath("$.data.items.length()", equalTo(1)))
            .andExpect(jsonPath("$.data.items[0].id", equalTo(programAId)))
            .andExpect(jsonPath("$.data.items[0].title", equalTo("청년 농업인 지원사업")))
            .andExpect(jsonPath("$.data.items[0].agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.items[0].eligibilitySummary", equalTo("자격 확인")))
            .andExpect(jsonPath("$.data.items[0].benefitSummary", equalTo("월 100만원 지원")))
            .andExpect(jsonPath("$.data.items[0].applicationPeriodLabel", equalTo("접수기관문의")))
            .andExpect(jsonPath("$.data.items[0].sourceUrl", equalTo("https://policy.example.com/a")))
            .andExpect(jsonPath("$.data.totalCount", equalTo(1)))
    }

    @Test
    fun `policy search with non matching keyword returns no items`() {
        mockMvc.perform(
            get("/api/v1/search/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("keyword", "존재하지않는키워드")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()", equalTo(0)))
            .andExpect(jsonPath("$.data.totalCount", equalTo(0)))
    }

    @Test
    fun `policy search without keyword returns every synced program`() {
        mockMvc.perform(
            get("/api/v1/search/policies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()", equalTo(2)))
            .andExpect(jsonPath("$.data.totalCount", equalTo(2)))
    }

    @Test
    fun `search all includes a policies section with the seeded program`() {
        mockMvc.perform(
            get("/api/v1/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.records.totalCount", equalTo(0)))
            .andExpect(jsonPath("$.data.posts.totalCount", equalTo(0)))
            .andExpect(jsonPath("$.data.policies.totalCount", equalTo(2)))
            .andExpect(jsonPath("$.data.policies.items[*].title", hasItem("청년 농업인 지원사업")))
    }

    private fun buildProgram(
        title: String,
        summary: String,
        benefitSummary: String,
        externalId: String,
        sourceUrl: String?,
        detailSynced: Boolean = true,
    ): PolicyProgram {
        return PolicyProgram(
            title = title,
            body = "정책 상세",
            region = "전국",
            targetManagementType = null,
        ).apply {
            applyListFields(
                source = PolicySource.NONGUP_EZ,
                externalId = externalId,
                sourceYear = "2026",
                title = title,
                summary = summary,
                region = "전국",
                sourceUrl = sourceUrl,
                agencyName = "농림축산식품부"
            )
            if (detailSynced) {
                applyDetailFields(
                    body = "정책 상세",
                    purpose = null,
                    eligibilityOriginal = null,
                    eligibilitySummary = "자격 확인",
                    benefitOriginal = null,
                    benefitSummary = benefitSummary,
                    applyStartsOn = null,
                    applyEndsOn = null,
                    applicationPeriodLabel = "접수기관문의",
                    applicationPeriodNotice = null,
                    applicationMethod = null,
                    requiredDocuments = null,
                    selectionCriteria = null,
                    departmentName = null,
                    onlineApplyAvailable = false,
                    applicationUrl = null,
                    targetTagsJson = "[]",
                    cropTagsJson = "[]",
                    regionTagsJson = "[]",
                    rawPayload = "{}",
                    recommendable = true
                )
            }
        }
    }
}
