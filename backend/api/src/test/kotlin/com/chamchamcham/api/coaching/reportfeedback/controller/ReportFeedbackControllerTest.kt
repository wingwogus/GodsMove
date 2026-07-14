package com.chamchamcham.api.coaching.reportfeedback.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackDetailResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackItemResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackListResult
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryService
import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackResultContent
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.farming.WorkType
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val reportId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val wateringFeedbackId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val fertilizingFeedbackId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val harvestFeedbackId = UUID.fromString("00000000-0000-0000-0000-000000000203")

    @MockBean private lateinit var queryService: ReportFeedbackQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `report feedback returns an ordered work type collection with ready content only`() {
        `when`(queryService.get(memberId, reportId)).thenReturn(
            ReportFeedbackListResult(
                reportId = reportId,
                feedbacks = listOf(
                    feedback(
                        feedbackId = wateringFeedbackId,
                        workType = WorkType.WATERING,
                        status = ReportFeedbackStatus.READY,
                        inputPrepared = true,
                        content = ReportFeedbackResultContent(
                            summary = "물 주기 작업을 살펴봤어요.",
                            comparisons = listOf(
                                ReportFeedbackItemResult("직전 재배보다 물 주기 기록이 한 번 늘었어요."),
                            ),
                            strengths = listOf(ReportFeedbackItemResult("흙이 마른 정도를 꾸준히 확인했어요.")),
                            improvements = listOf(ReportFeedbackItemResult("물을 준 간격도 함께 비교해 보세요.")),
                            nextActions = listOf(ReportFeedbackItemResult("내일 흙이 마른 정도를 다시 확인하세요.")),
                        ),
                    ),
                    feedback(
                        feedbackId = fertilizingFeedbackId,
                        workType = WorkType.FERTILIZING,
                        status = ReportFeedbackStatus.PENDING,
                    ),
                    feedback(
                        feedbackId = harvestFeedbackId,
                        workType = WorkType.HARVEST,
                        status = ReportFeedbackStatus.FAILED,
                        inputPrepared = true,
                        failureCode = "STRUCTURED_OUTPUT_INVALID",
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}/feedback", reportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
            .andExpect(jsonPath("$.data.reportId", equalTo(reportId.toString())))
            .andExpect(jsonPath("$.data.feedbacks.length()", equalTo(3)))
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId", equalTo(wateringFeedbackId.toString())))
            .andExpect(jsonPath("$.data.feedbacks[0].workType", equalTo("WATERING")))
            .andExpect(jsonPath("$.data.feedbacks[0].status", equalTo("READY")))
            .andExpect(jsonPath("$.data.feedbacks[0].inputPrepared", equalTo(true)))
            .andExpect(jsonPath("$.data.feedbacks[0].failureCode").isEmpty())
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.summary", equalTo("물 주기 작업을 살펴봤어요.")))
            .andExpect(
                jsonPath(
                    "$.data.feedbacks[0].feedback.comparisons[0].text",
                    equalTo("직전 재배보다 물 주기 기록이 한 번 늘었어요."),
                ),
            )
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.strengths[0].text", equalTo("흙이 마른 정도를 꾸준히 확인했어요.")))
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.improvements[0].text", equalTo("물을 준 간격도 함께 비교해 보세요.")))
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.nextActions[0].text", equalTo("내일 흙이 마른 정도를 다시 확인하세요.")))
            .andExpect(jsonPath("$.data.feedbacks[0].createdAt", equalTo("2026-07-14T10:00:00")))
            .andExpect(jsonPath("$.data.feedbacks[0].updatedAt", equalTo("2026-07-14T10:01:00")))
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.strengths[0].basis").doesNotExist())
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.comparisons[0].basis").doesNotExist())
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.nextCycleActions").doesNotExist())
            .andExpect(jsonPath("$.data.feedbacks[0].feedback.citations").doesNotExist())
            .andExpect(jsonPath("$.data.feedbacks[0].citations").doesNotExist())
            .andExpect(jsonPath("$.data.feedbacks[1].workType", equalTo("FERTILIZING")))
            .andExpect(jsonPath("$.data.feedbacks[1].status", equalTo("PENDING")))
            .andExpect(jsonPath("$.data.feedbacks[1].inputPrepared", equalTo(false)))
            .andExpect(jsonPath("$.data.feedbacks[1].feedback").isEmpty())
            .andExpect(jsonPath("$.data.feedbacks[2].workType", equalTo("HARVEST")))
            .andExpect(jsonPath("$.data.feedbacks[2].status", equalTo("FAILED")))
            .andExpect(jsonPath("$.data.feedbacks[2].failureCode", equalTo("STRUCTURED_OUTPUT_INVALID")))
            .andExpect(jsonPath("$.data.feedbacks[2].feedback").isEmpty())
            .andExpect(jsonPath("$.data.status").doesNotExist())
            .andExpect(jsonPath("$.data.feedback").doesNotExist())

    }

    @Test
    fun `completed report without generated feedback returns an empty collection`() {
        `when`(queryService.get(memberId, reportId)).thenReturn(
            ReportFeedbackListResult(reportId = reportId, feedbacks = emptyList()),
        )

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}/feedback", reportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reportId", equalTo(reportId.toString())))
            .andExpect(jsonPath("$.data.feedbacks").isArray)
            .andExpect(jsonPath("$.data.feedbacks").isEmpty())
    }

    @Test
    fun `report feedback without principal is unauthorized`() {
        mockMvc.perform(get("/api/v1/farming-reports/{reportId}/feedback", reportId))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `report feedback propagates report not found`() {
        `when`(queryService.get(memberId, reportId))
            .thenThrow(BusinessException(ErrorCode.REPORT_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farming-reports/{reportId}/feedback", reportId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("REPORT_001")))
    }

    @Test
    fun `failed work type feedback can be regenerated`() {
        `when`(queryService.regenerate(memberId, reportId, WorkType.HARVEST)).thenReturn(
            feedback(
                feedbackId = harvestFeedbackId,
                workType = WorkType.HARVEST,
                status = ReportFeedbackStatus.PENDING,
            ),
        )

        mockMvc.perform(
            post(
                "/api/v1/farming-reports/{reportId}/feedback/{workType}/regenerate",
                reportId,
                WorkType.HARVEST,
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.feedbackId", equalTo(harvestFeedbackId.toString())))
            .andExpect(jsonPath("$.data.workType", equalTo("HARVEST")))
            .andExpect(jsonPath("$.data.status", equalTo("PENDING")))
            .andExpect(jsonPath("$.data.inputPrepared", equalTo(false)))
            .andExpect(jsonPath("$.data.failureCode").isEmpty())
            .andExpect(jsonPath("$.data.feedback").isEmpty())
    }

    @Test
    fun `regeneration conflict is returned when work type feedback is not failed`() {
        `when`(queryService.regenerate(memberId, reportId, WorkType.WATERING))
            .thenThrow(BusinessException(ErrorCode.REPORT_FEEDBACK_REGENERATION_NOT_ALLOWED))

        mockMvc.perform(
            post(
                "/api/v1/farming-reports/{reportId}/feedback/{workType}/regenerate",
                reportId,
                WorkType.WATERING,
            ).with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code", equalTo("COACHING_004")))
    }

    private fun feedback(
        feedbackId: UUID,
        workType: WorkType,
        status: ReportFeedbackStatus,
        inputPrepared: Boolean = false,
        failureCode: String? = null,
        content: ReportFeedbackResultContent? = null,
    ) = ReportFeedbackDetailResult(
        feedbackId = feedbackId,
        workType = workType,
        status = status,
        inputPrepared = inputPrepared,
        failureCode = failureCode,
        content = content,
        createdAt = LocalDateTime.of(2026, 7, 14, 10, 0),
        updatedAt = LocalDateTime.of(2026, 7, 14, 10, 1),
    )

    private fun authenticatedMember(memberId: String): RequestPostProcessor = RequestPostProcessor { request ->
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            memberId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        request
    }
}
