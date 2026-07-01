package com.godsmove.api.coaching.controller

import com.godsmove.api.exception.GlobalExceptionHandler
import com.godsmove.application.coaching.rag.ChatCompletionClient
import com.godsmove.application.coaching.rag.ChatMessage
import com.godsmove.application.coaching.rag.CoachingRagService
import com.godsmove.application.coaching.rag.EmbeddingClient
import com.godsmove.application.coaching.rag.RagCitationAuditor
import com.godsmove.application.coaching.rag.RagEvidenceChunk
import com.godsmove.application.coaching.rag.RagIndexRepository
import com.godsmove.application.coaching.rag.RagPromptBuilder
import com.godsmove.application.coaching.rag.RagProperties
import com.godsmove.application.coaching.rag.RagRetrievalFilter
import com.godsmove.application.coaching.rag.RagSourceType
import com.godsmove.application.security.TokenProvider
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

@WebMvcTest(CoachingRagController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, CoachingRagControllerTest.TestRagConfig::class)
class CoachingRagControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `query returns RAG answer`() {
        mockMvc.perform(
            post("/api/v1/coaching/rag/query")
                .with(user("00000000-0000-0000-0000-000000000001"))
                .principal(Principal { "00000000-0000-0000-0000-000000000001" })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"과습 위험이 있을까?","topK":6}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.answer", equalTo("답변 [chunk:00000000-0000-0000-0000-000000000101]")))
            .andExpect(jsonPath("$.data.model.embedding", equalTo("bge-m3")))
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

    @TestConfiguration
    class TestRagConfig {
        private val chunkId = UUID.fromString("00000000-0000-0000-0000-000000000101")

        @Bean
        fun coachingRagService(): CoachingRagService {
            return CoachingRagService(
                embeddingClient = object : EmbeddingClient {
                    override fun embed(input: String, model: String): List<Double> = List(1024) { 0.1 }
                },
                chatCompletionClient = object : ChatCompletionClient {
                    override fun complete(messages: List<ChatMessage>, model: String): String {
                        return "답변 [chunk:$chunkId]"
                    }
                },
                ragIndexRepository = object : RagIndexRepository {
                    override fun retrieve(
                        embedding: List<Double>,
                        filters: RagRetrievalFilter,
                        topK: Int
                    ): List<RagEvidenceChunk> {
                        return listOf(
                            RagEvidenceChunk(
                                id = chunkId,
                                sourceType = RagSourceType.FARMING_RECORD,
                                sourceId = "00000000-0000-0000-0000-000000000201",
                                content = "관수 후 배수 상태를 확인했다.",
                                label = "영농일지 관수",
                                similarityScore = 0.82
                            )
                        )
                    }
                },
                promptBuilder = RagPromptBuilder(),
                citationAuditor = RagCitationAuditor(),
                ragProperties = RagProperties()
            )
        }
    }
}
