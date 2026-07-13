package com.chamchamcham.api.coaching.reportfeedback.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackDetailResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryService
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(ReportFeedbackController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class ReportFeedbackControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val memberId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()

    @MockBean private lateinit var queryService: ReportFeedbackQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `ready report feedback exposes sectioned display content only`() {
        `when`(queryService.get(memberId, reportId)).thenReturn(
            ReportFeedbackDetailResult(
                feedbackId = feedbackId,
                reportId = reportId,
                status = ReportFeedbackStatus.READY,
                inputPrepared = true,
                failureCode = null,
                content = ReportFeedbackResultContent(
                    summary = "이번 사이클 요약",
                    strengths = listOf(ReportFeedbackItemResult("관수 기록을 꾸준히 남겼습니다.")),
                    improvements = listOf(ReportFeedbackItemResult("시비 간격을 비교하세요.")),
                    nextCycleActions = listOf(ReportFeedbackItemResult("파종 전 토양 상태를 기록하세요.")),
                ),
                createdAt = LocalDateTime.of(2026, 7, 13, 9, 0),
                updatedAt = LocalDateTime.of(2026, 7, 13, 9, 1),
            ),
        )

        mockMvc.perform(get("/api/v1/farming-reports/{reportId}/feedback", reportId).with(authenticatedMember(memberId.toString())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("READY")))
            .andExpect(jsonPath("$.data.feedback.summary", equalTo("이번 사이클 요약")))
            .andExpect(jsonPath("$.data.feedback.strengths[0].text", equalTo("관수 기록을 꾸준히 남겼습니다.")))
            .andExpect(jsonPath("$.data.feedback.strengths[0].basis").doesNotExist())
            .andExpect(jsonPath("$.data.citations").doesNotExist())
    }

    @Test
    fun `report feedback without principal is unauthorized`() {
        mockMvc.perform(get("/api/v1/farming-reports/{reportId}/feedback", reportId))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor = RequestPostProcessor { request ->
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            memberId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        request
    }
}
