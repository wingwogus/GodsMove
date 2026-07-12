package com.chamchamcham.api.coaching

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.coaching.rag.common.RagProperties
import com.chamchamcham.application.coaching.rag.common.RagSourceType
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationService
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackRetrievalQueryPlanner
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path

@Tag("local-smoke")
@ActiveProfiles("local")
@SpringBootTest(classes = [ApiApplication::class])
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_RAG_SMOKE", matches = "true")
class RecordFeedbackGenerationVectorStoreSmokeTest @Autowired constructor(
    private val vectorStore: VectorStore,
    private val ragProperties: RagProperties,
) {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val planner = RecordFeedbackRetrievalQueryPlanner()

    @Test
    fun `fake watering payload retrieves real tech document chunks from vector store`() {
        val context = readFixture("today-record-feedback-watering.json")
        val queries = planner.plan(context)
        val cropName = context.crop.name.trim()

        val retrieved = queries.flatMap { query ->
            vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(query.query)
                    .topK(3)
                    .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
                    .filterExpression(
                        "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' && " +
                            "cropName in ['$cropName', '${RecordFeedbackGenerationService.GENERAL_CROP_NAME}']",
                    )
                    .build(),
            )
        }.distinctBy { it.id }

        assertThat(queries.map { it.query }).contains("참당귀 관수 재배 관리 약용작물")
        assertThat(retrieved)
            .withFailMessage("Seed real PDF chunks first with the local dev RAG seed endpoint in TECH_DOCUMENT-only mode.")
            .isNotEmpty
        assertThat(retrieved).allSatisfy { document ->
            assertThat(document.metadata["sourceType"]).isEqualTo(RagSourceType.TECH_DOCUMENT.name)
            assertThat(document.text).isNotBlank()
        }
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val path = Path.of("application/src/test/resources/coaching/rag", name)
        return objectMapper.readValue(Files.readString(path), RecordFeedbackContext::class.java)
    }
}
