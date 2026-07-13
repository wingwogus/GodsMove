package com.chamchamcham.api.coaching.chat.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.coaching.chat.CoachingRagCommand
import com.chamchamcham.application.coaching.chat.CoachingRagResult
import com.chamchamcham.application.coaching.chat.CoachingRagService
import com.chamchamcham.application.coaching.chat.CoachingActionDue
import com.chamchamcham.application.coaching.chat.CoachingCitationRef
import com.chamchamcham.application.coaching.chat.CoachingNextAction
import com.chamchamcham.application.coaching.chat.CoachingObservation
import com.chamchamcham.application.coaching.chat.CoachingPriority
import com.chamchamcham.application.coaching.chat.CoachingRecommendation
import com.chamchamcham.application.coaching.chat.CoachingRiskLevel
import com.chamchamcham.application.coaching.chat.CoachingStructuredResult
import com.chamchamcham.application.coaching.common.RagAuditResult
import com.chamchamcham.application.coaching.common.RagAuditStatus
import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.common.RagSourceType
import com.chamchamcham.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal

@WebMvcTest(CoachingRagController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class CoachingRagControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @MockBean
    private lateinit var coachingRagService: CoachingRagService

    @Test
    fun `query returns RAG answer`() {
        doReturn(ragResult())
            .`when`(coachingRagService)
            .answer(anyCommand())

        mockMvc.perform(
            post("/api/v1/coaching/rag/query")
                .with(user("00000000-0000-0000-0000-000000000001"))
                .principal(Principal { "00000000-0000-0000-0000-000000000001" })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"과습 위험이 있을까?","topK":6}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.result.summary", equalTo("요약")))
            .andExpect(jsonPath("$.data.result.riskLevel", equalTo("LOW")))
            .andExpect(jsonPath("$.data.audit.status", equalTo("PASS")))
            .andExpect(jsonPath("$.data.model.embedding", equalTo("bge-m3")))
            .andExpect(jsonPath("$.data.model.chat", equalTo("openclaw/agri-rag-coach")))
            .andExpect(jsonPath("$.data.result.observations[0].citationLabels[0]", equalTo("근거 1: 농업기술길잡이 007 약용작물")))
            .andExpect(jsonPath("$.data.result.recommendations[0].citationLabels[0]", equalTo("근거 1: 농업기술길잡이 007 약용작물")))
            .andExpect(jsonPath("$.data.result.nextActions[0].citationLabels[0]", equalTo("근거 1: 농업기술길잡이 007 약용작물")))
            .andExpect(jsonPath("$.data.result.citations[0].displayLabel", equalTo("근거 1")))
            .andExpect(jsonPath("$.data.savedFeedbackId").doesNotExist())
    }

    @Test
    fun `query rejects blank question`() {
        mockMvc.perform(
            post("/api/v1/coaching/rag/query")
                .with(user("00000000-0000-0000-0000-000000000001"))
                .principal(Principal { "00000000-0000-0000-0000-000000000001" })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    private fun ragResult(): CoachingRagResult {
        return CoachingRagResult(
            result = CoachingStructuredResult(
                summary = "요약",
                riskLevel = CoachingRiskLevel.LOW,
                confidence = 0.8,
                observations = listOf(CoachingObservation("관수", "토양 수분 확인", listOf("doc-1"))),
                diagnosis = "진단",
                recommendations = listOf(
                    CoachingRecommendation(CoachingPriority.MEDIUM, "토양 확인", "공식문서 근거", null, listOf("doc-1"))
                ),
                nextActions = listOf(CoachingNextAction(CoachingActionDue.TODAY, "기록 보완", listOf("doc-1"))),
                followUpQuestions = emptyList(),
                citations = listOf(
                    CoachingCitationRef("doc-1", "농업기술길잡이 007 약용작물", RagSourceType.TECH_DOCUMENT)
                )
            ),
            audit = RagAuditResult(RagAuditStatus.PASS, emptyList(), emptyList()),
            model = RagModelInfo(
                embedding = "bge-m3",
                chat = "openclaw/agri-rag-coach"
            )
        )
    }

    private fun anyCommand(): CoachingRagCommand {
        any(CoachingRagCommand::class.java)
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}
