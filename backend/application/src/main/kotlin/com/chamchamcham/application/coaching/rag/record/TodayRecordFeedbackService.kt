package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.coaching.rag.common.CoachingRiskLevel
import com.chamchamcham.application.coaching.rag.common.CoachingStructuredOutputValidator
import com.chamchamcham.application.coaching.rag.common.CoachingStructuredResult
import com.chamchamcham.application.coaching.rag.common.RagAuditResult
import com.chamchamcham.application.coaching.rag.common.RagAuditStatus
import com.chamchamcham.application.coaching.rag.common.RagModelInfo
import com.chamchamcham.application.coaching.rag.common.RagProperties
import com.chamchamcham.application.coaching.rag.common.RagSourceType
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

data class TodayRecordFeedbackResult(
    val result: CoachingStructuredResult,
    val audit: RagAuditResult,
    val model: RagModelInfo,
    val contextWarnings: List<String>
)

@Service
class TodayRecordFeedbackService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextValidator: TodayRecordFeedbackContextValidator,
    private val queryPlanner: RecordFeedbackRetrievalQueryPlanner,
    private val promptBuilder: RecordFeedbackPromptBuilder,
    private val outputValidator: CoachingStructuredOutputValidator,
    private val ragProperties: RagProperties
) {
    fun generate(context: TodayRecordFeedbackContext, topK: Int? = null): TodayRecordFeedbackResult {
        val validation = contextValidator.requireValid(context)
        val perQueryTopK = normalizeTopK(topK)
        val queries = queryPlanner.plan(context)
        val documents = retrieveDocuments(queries, perQueryTopK)

        if (documents.isEmpty()) {
            val result = CoachingStructuredResult.insufficientEvidence(
                "현재 공식문서 근거만으로는 기록을 판단할 수 없습니다."
            ).copy(
                riskLevel = CoachingRiskLevel.UNKNOWN,
                limitations = listOf("검색된 공식문서 근거가 없습니다.")
            )
            return TodayRecordFeedbackResult(
                result = result,
                audit = RagAuditResult(RagAuditStatus.WARN, listOf("no_retrieved_documents"), emptyList()),
                model = modelInfo(),
                contextWarnings = validation.warnings
            )
        }

        val evidence = documents.map { it.toRecordFeedbackEvidence() }
        val prompt = promptBuilder.build(context, queries, evidence)

        val result = try {
            chatClient.prompt()
                .system(prompt.system)
                .user(prompt.user)
                .call()
                .entity(CoachingStructuredResult::class.java)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RuntimeException) {
            throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
        }

        val allowedCitationIds = documents.map { it.id }.toSet() + context.recordCitationId()
        val audit = outputValidator.validate(result, allowedCitationIds)

        return TodayRecordFeedbackResult(
            result = result,
            audit = audit,
            model = modelInfo(),
            contextWarnings = validation.warnings
        )
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return value
    }

    private fun retrieveDocuments(
        queries: List<RecordFeedbackRetrievalQuery>,
        perQueryTopK: Int
    ): List<Document> {
        return queries
            .flatMap { query ->
                vectorStore.similaritySearch(
                    SearchRequest.builder()
                        .query(query.query)
                        .topK(perQueryTopK)
                        .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
                        .filterExpression("sourceType == '${RagSourceType.TECH_DOCUMENT.name}'")
                        .build()
                )
            }
            .distinctBy { it.id }
    }

    private fun Document.toRecordFeedbackEvidence(): RecordFeedbackEvidence {
        return RecordFeedbackEvidence(
            id = id,
            title = metadata["documentTitle"]?.toString()
                ?: metadata["label"]?.toString()
                ?: id,
            page = metadata["page"]?.toString()?.toIntOrNull(),
            content = text ?: ""
        )
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
