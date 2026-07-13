package com.chamchamcham.api.coaching.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackQueryService
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackNextActionResult
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackResultContent
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackDetailResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.coaching.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.RecordFeedbackStatus
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

@WebMvcTest(RecordFeedbackController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class RecordFeedbackControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val feedbackId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @MockBean private lateinit var queryService: RecordFeedbackQueryService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `get status returns only phase two readiness fields`() {
        `when`(queryService.get(memberId, recordId)).thenReturn(result(status = RecordFeedbackStatus.PENDING, inputPrepared = true))

        mockMvc.perform(
            get("/api/v1/farming-records/{recordId}/feedback", recordId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
            .andExpect(jsonPath("$.data.feedbackId", equalTo(feedbackId.toString())))
            .andExpect(jsonPath("$.data.recordId", equalTo(recordId.toString())))
            .andExpect(jsonPath("$.data.status", equalTo("PENDING")))
            .andExpect(jsonPath("$.data.sourceRevision", equalTo(3)))
            .andExpect(jsonPath("$.data.inputPrepared", equalTo(true)))
            .andExpect(jsonPath("$.data.failureCode").isEmpty())
            .andExpect(jsonPath("$.data.feedback").isEmpty())
            .andExpect(jsonPath("$.data.inputSnapshot").doesNotExist())
    }

    @Test
    fun `ready response serializes feedback but not internal coaching fields`() {
        `when`(queryService.get(memberId, recordId)).thenReturn(
            result(
                status = RecordFeedbackStatus.READY,
                content = RecordFeedbackResultContent(
                    goodPoint = "점적관수로 토양 수분을 확인한 점이 좋았어요.",
                    nextActions = listOf(
                        RecordFeedbackNextActionResult(
                            text = "오후에 토양 표면이 마르는지 한 번 더 확인하세요.",
                            due = RecordFeedbackActionDue.TODAY,
                            category = RecordFeedbackActionCategory.IRRIGATION,
                        ),
                        RecordFeedbackNextActionResult(
                            text = "이번 주 안에 배수로 주변의 막힌 흙을 정리하세요.",
                            due = RecordFeedbackActionDue.THIS_WEEK,
                            category = RecordFeedbackActionCategory.CULTIVATION,
                        ),
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/api/v1/farming-records/{recordId}/feedback", recordId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("READY")))
            .andExpect(jsonPath("$.data.feedback.goodPoint.text", equalTo("점적관수로 토양 수분을 확인한 점이 좋았어요.")))
            .andExpect(jsonPath("$.data.feedback.goodPoint.basis").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.goodPoint.evidenceRefs").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.nextActions[0].text", equalTo("오후에 토양 표면이 마르는지 한 번 더 확인하세요.")))
            .andExpect(jsonPath("$.data.feedback.nextActions[0].due", equalTo("TODAY")))
            .andExpect(jsonPath("$.data.feedback.nextActions[0].category", equalTo("IRRIGATION")))
            .andExpect(jsonPath("$.data.feedback.nextActions[0].basis").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.nextActions[0].evidenceRefs").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.citations").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.inputSnapshot").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.audit").doesNotExist())
            .andExpect(jsonPath("$.data.feedback.model").doesNotExist())
            .andExpect(jsonPath("$.data.citations").doesNotExist())
            .andExpect(jsonPath("$.data.auditStatus").doesNotExist())
            .andExpect(jsonPath("$.data.modelName").doesNotExist())
    }

    @Test
    fun `regenerate returns retried pending status`() {
        `when`(queryService.regenerate(memberId, recordId)).thenReturn(result(status = RecordFeedbackStatus.PENDING))

        mockMvc.perform(
            post("/api/v1/farming-records/{recordId}/feedback/regenerate", recordId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status", equalTo("PENDING")))
            .andExpect(jsonPath("$.data.failureCode").isEmpty())
    }

    @Test
    fun `get status without principal returns unauthorized`() {
        mockMvc.perform(get("/api/v1/farming-records/{recordId}/feedback", recordId))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `regenerate with invalid principal returns unauthorized`() {
        mockMvc.perform(
            post("/api/v1/farming-records/{recordId}/feedback/regenerate", recordId)
                .with(authenticatedMember("not-a-uuid")),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `get status propagates owned record not found`() {
        `when`(queryService.get(memberId, recordId))
            .thenThrow(BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farming-records/{recordId}/feedback", recordId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("FARMING_001")))
    }

    @Test
    fun `regenerate propagates conflict for a nonfailed or noncurrent feedback`() {
        `when`(queryService.regenerate(memberId, recordId))
            .thenThrow(BusinessException(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED))

        mockMvc.perform(
            post("/api/v1/farming-records/{recordId}/feedback/regenerate", recordId)
                .with(authenticatedMember(memberId.toString())),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code", equalTo("COACHING_002")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor = RequestPostProcessor { request ->
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            memberId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        request
    }

    private fun result(
        status: RecordFeedbackStatus,
        inputPrepared: Boolean = false,
        failureCode: String? = null,
        content: RecordFeedbackResultContent? = null,
    ) = RecordFeedbackDetailResult(
        feedbackId = feedbackId,
        recordId = recordId,
        status = status,
        sourceRevision = 3,
        inputPrepared = inputPrepared,
        failureCode = failureCode,
        content = content,
        createdAt = LocalDateTime.of(2026, 7, 11, 10, 0),
        updatedAt = LocalDateTime.of(2026, 7, 11, 10, 1),
    )
}
