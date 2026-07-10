package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.policy.recommendation.PolicyRecommendationResult
import com.chamchamcham.application.policy.recommendation.PolicyRecommendationService
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.policy.PolicyRecommendationSort
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(PolicyController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class PolicyControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recommendationId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val policyProgramId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @MockBean
    private lateinit var policyRecommendationService: PolicyRecommendationService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `list recommendations maps authenticated principal and returns items plus next cursor`() {
        `when`(
            policyRecommendationService.listRecommendations(
                memberId,
                null,
                20,
                null,
                PolicyRecommendationSort.RECOMMENDED
            )
        )
            .thenReturn(
                PolicyRecommendationResult.Page(
                    items = listOf(
                        PolicyRecommendationResult.Card(
                            recommendationId = recommendationId,
                            policyProgramId = policyProgramId,
                            programTitle = "청년농 정착 지원",
                            eligibilitySummary = "만 40세 미만",
                            benefitSummary = "월 최대 110만원",
                            applicationPeriodLabel = "2026.01.01 - 2026.02.28",
                            agencyName = "농림축산식품부",
                            score = BigDecimal("87.50"),
                            reason = "재배 작물과 지역 조건이 일치합니다"
                        )
                    ),
                    nextCursor = "cursor-2"
                )
            )

        mockMvc.perform(
            get("/api/v1/policy-recommendations")
                .param("size", "20")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].recommendationId", equalTo(recommendationId.toString())))
            .andExpect(jsonPath("$.data.items[0].policyProgramId", equalTo(policyProgramId.toString())))
            .andExpect(jsonPath("$.data.items[0].programTitle", equalTo("청년농 정착 지원")))
            .andExpect(jsonPath("$.data.items[0].eligibilitySummary", equalTo("만 40세 미만")))
            .andExpect(jsonPath("$.data.items[0].benefitSummary", equalTo("월 최대 110만원")))
            .andExpect(jsonPath("$.data.items[0].applicationPeriodLabel", equalTo("2026.01.01 - 2026.02.28")))
            .andExpect(jsonPath("$.data.items[0].agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.items[0].score", equalTo(87.50)))
            .andExpect(jsonPath("$.data.items[0].reason", equalTo("재배 작물과 지역 조건이 일치합니다")))
            .andExpect(jsonPath("$.data.nextCursor", equalTo("cursor-2")))

        verify(policyRecommendationService).listRecommendations(
            memberId,
            null,
            20,
            null,
            PolicyRecommendationSort.RECOMMENDED
        )
    }

    @Test
    fun `list recommendations passes benefit category filter to service`() {
        `when`(
            policyRecommendationService.listRecommendations(
                memberId,
                null,
                20,
                PolicyBenefitCategory.FINANCE,
                PolicyRecommendationSort.RECOMMENDED
            )
        ).thenReturn(PolicyRecommendationResult.Page(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/policy-recommendations")
                .param("benefitCategory", "FINANCE")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)

        verify(policyRecommendationService).listRecommendations(
            memberId,
            null,
            20,
            PolicyBenefitCategory.FINANCE,
            PolicyRecommendationSort.RECOMMENDED
        )
    }

    @Test
    fun `list recommendations passes latest sort to service`() {
        `when`(
            policyRecommendationService.listRecommendations(
                memberId,
                null,
                20,
                null,
                PolicyRecommendationSort.LATEST
            )
        ).thenReturn(PolicyRecommendationResult.Page(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/policy-recommendations")
                .param("sort", "LATEST")
                .with(authenticatedMember(memberId.toString()))
        ).andExpect(status().isOk)

        verify(policyRecommendationService).listRecommendations(
            memberId,
            null,
            20,
            null,
            PolicyRecommendationSort.LATEST
        )
    }

    @Test
    fun `list recommendations rejects unknown sort`() {
        mockMvc.perform(
            get("/api/v1/policy-recommendations")
                .param("sort", "BAD")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo(ErrorCode.INVALID_INPUT.code)))

        verifyNoInteractions(policyRecommendationService)
    }

    @Test
    fun `list recommendations rejects unknown benefit category`() {
        mockMvc.perform(
            get("/api/v1/policy-recommendations")
                .param("benefitCategory", "BAD")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo(ErrorCode.INVALID_INPUT.code)))

        verifyNoInteractions(policyRecommendationService)
    }

    @Test
    fun `get program detail returns detail sections contacts and attachments`() {
        `when`(policyRecommendationService.getProgramDetail(memberId, policyProgramId))
            .thenReturn(
                PolicyRecommendationResult.Detail(
                    id = policyProgramId,
                    programTitle = "청년농 정착 지원",
                    sourceYear = "2026",
                    agencyName = "농림축산식품부",
                    departmentName = "청년농육성팀",
                    applicationPeriodLabel = "2026.01.01 - 2026.02.28",
                    onlineApplyAvailable = true,
                    sourceUrl = "https://www.nongupez.go.kr/detail",
                    applicationUrl = "https://www.nongupez.go.kr/apply",
                    purpose = "영농 초기 정착 지원",
                    summary = "청년농을 위한 정착 지원",
                    eligibility = "만 40세 미만 농업인",
                    benefit = "정착지원금",
                    applicationMethod = "온라인 신청",
                    requiredDocuments = "사업계획서",
                    selectionCriteria = "영농 의지",
                    contacts = listOf(
                        PolicyRecommendationResult.Contact(
                            agencyName = "농림축산식품부",
                            departmentName = "청년농육성팀",
                            phoneNumber = "044-000-0000"
                        )
                    ),
                    attachments = listOf(
                        PolicyRecommendationResult.Attachment(
                            fileName = "guide.pdf",
                            extension = "pdf",
                            sizeBytes = 1024,
                            url = "https://www.nongupez.go.kr/guide.pdf"
                        )
                    )
                )
            )

        mockMvc.perform(
            get("/api/v1/policy-programs/{policyProgramId}", policyProgramId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id", equalTo(policyProgramId.toString())))
            .andExpect(jsonPath("$.data.programTitle", equalTo("청년농 정착 지원")))
            .andExpect(jsonPath("$.data.eligibility", equalTo("만 40세 미만 농업인")))
            .andExpect(jsonPath("$.data.benefit", equalTo("정착지원금")))
            .andExpect(jsonPath("$.data.applicationPeriodLabel", equalTo("2026.01.01 - 2026.02.28")))
            .andExpect(jsonPath("$.data.agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.contacts[0].agencyName", equalTo("농림축산식품부")))
            .andExpect(jsonPath("$.data.contacts[0].departmentName", equalTo("청년농육성팀")))
            .andExpect(jsonPath("$.data.contacts[0].phoneNumber", equalTo("044-000-0000")))
            .andExpect(jsonPath("$.data.attachments[0].fileName", equalTo("guide.pdf")))
            .andExpect(jsonPath("$.data.attachments[0].extension", equalTo("pdf")))
            .andExpect(jsonPath("$.data.attachments[0].sizeBytes", equalTo(1024)))
            .andExpect(jsonPath("$.data.attachments[0].url", equalTo("https://www.nongupez.go.kr/guide.pdf")))

        verify(policyRecommendationService).getProgramDetail(memberId, policyProgramId)
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }
}
