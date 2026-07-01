package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.retrieval.search.DocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CoachingRagService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextProvider: CoachingContextProvider,
    private val filterBuilder: CoachingRetrievalFilterBuilder,
    private val validator: CoachingStructuredOutputValidator,
    private val persistencePolicy: CoachingFeedbackPersistencePolicy,
    private val ragProperties: RagProperties
) {
    @Transactional
    fun answer(command: CoachingRagCommand): CoachingRagResult {
        val normalizedQuestion = normalizeQuestion(command.question)
        val topK = normalizeTopK(command.topK)
        validatePeriod(command)

        val filterExpression = filterBuilder.build(command)
        val retrievedDocuments = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(normalizedQuestion)
                .topK(topK)
                .filterExpression(filterExpression)
                .build()
        )

        if (retrievedDocuments.isEmpty()) {
            val result = CoachingStructuredResult.insufficientEvidence(
                "현재 자료만으로는 판단할 수 없습니다. 영농일지나 기술문서 색인 상태를 확인해주세요."
            )
            return CoachingRagResult(
                result = result,
                audit = RagAuditResult(RagAuditStatus.WARN, listOf("no_retrieved_documents"), emptyList()),
                model = modelInfo()
            )
        }

        val context = contextProvider.build(command)
        val advisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(DocumentRetriever { retrievedDocuments })
            .build()

        val result = try {
            chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(normalizedQuestion, context))
                .advisors(advisor)
                .call()
                .entity(CoachingStructuredResult::class.java)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RuntimeException) {
            throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
        }

        val allowedCitationIds = retrievedDocuments.map { it.id }.toSet()
        val audit = validator.validate(result, allowedCitationIds)
        val savedFeedbackId = if (persistencePolicy.shouldSave(command)) {
            null
        } else {
            null
        }

        return CoachingRagResult(
            result = result,
            audit = audit,
            model = modelInfo(),
            savedFeedbackId = savedFeedbackId
        )
    }

    private fun normalizeQuestion(question: String): String {
        val normalized = question.trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return normalized
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return value
    }

    private fun validatePeriod(command: CoachingRagCommand) {
        if (command.periodStart != null && command.periodEnd != null && command.periodStart.isAfter(command.periodEnd)) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
    }

    private fun systemPrompt(): String {
        return """
            너는 농업 영농 코칭 보조자다.
            반드시 제공된 재배 context와 검색 근거만 사용한다.
            근거가 부족하면 riskLevel은 UNKNOWN, confidence는 0.3 이하로 둔다.
            모든 권장 작업과 다음 행동에는 근거 citationIds를 포함한다.
            응답은 요청된 JSON schema만 따른다.
        """.trimIndent()
    }

    private fun userPrompt(question: String, context: CoachingContext): String {
        return """
            질문:
            $question

            ${context.text}
        """.trimIndent()
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
