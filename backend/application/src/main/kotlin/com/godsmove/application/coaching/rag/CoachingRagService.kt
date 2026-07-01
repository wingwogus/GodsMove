package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.stereotype.Service

@Service
class CoachingRagService(
    private val embeddingClient: EmbeddingClient,
    private val chatCompletionClient: ChatCompletionClient,
    private val ragIndexRepository: RagIndexRepository,
    private val promptBuilder: RagPromptBuilder,
    private val citationAuditor: RagCitationAuditor,
    private val ragProperties: RagProperties
) {
    fun answer(command: CoachingRagCommand): CoachingRagResult {
        val normalizedQuestion = normalizeQuestion(command.question)
        val topK = normalizeTopK(command.topK)
        validatePeriod(command)

        val embedding = embeddingClient.embed(normalizedQuestion, ragProperties.embedding.model)
        if (embedding.size != ragProperties.embedding.dimension) {
            throw BusinessException(ErrorCode.RAG_EMBEDDING_DIMENSION_MISMATCH)
        }

        val chunks = ragIndexRepository.retrieve(
            embedding = embedding,
            filters = command.toRetrievalFilter(),
            topK = topK
        )

        if (chunks.isEmpty()) {
            val audit = RagAuditResult(
                status = RagAuditStatus.WARN,
                warnings = listOf("no_retrieved_chunks"),
                citations = emptyList()
            )
            return CoachingRagResult(
                result = CoachingStructuredResult.insufficientEvidence(
                    "현재 자료만으로는 판단할 수 없습니다. 영농일지나 기술문서 색인 상태를 확인해주세요."
                ),
                audit = audit,
                model = modelInfo()
            )
        }

        var answerText = chatCompletionClient.complete(
            messages = promptBuilder.buildPrompt(normalizedQuestion, chunks),
            model = ragProperties.chat.model
        )
        var audit = citationAuditor.audit(
            answerText = answerText,
            retrievedChunks = chunks,
            lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
        )

        if (citationAuditor.shouldRetry(audit)) {
            answerText = chatCompletionClient.complete(
                messages = promptBuilder.buildCitationRetryPrompt(normalizedQuestion, chunks),
                model = ragProperties.chat.model
            )
            audit = citationAuditor.audit(
                answerText = answerText,
                retrievedChunks = chunks,
                lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
            )
        }

        if (citationAuditor.shouldRetry(audit)) {
            answerText = citationAuditor.buildExtractiveFallbackAnswer(
                question = normalizedQuestion,
                retrievedChunks = chunks
            )
            val fallbackAudit = citationAuditor.audit(
                answerText = answerText,
                retrievedChunks = chunks,
                lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
            )
            audit = fallbackAudit.copy(
                status = RagAuditStatus.WARN,
                warnings = (fallbackAudit.warnings + "citation_retry_failed_used_extractive_fallback").distinct()
            )
        }

        return CoachingRagResult(
            result = toStructuredResult(answerText, audit, chunks),
            audit = audit,
            model = modelInfo()
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

    private fun CoachingRagCommand.toRetrievalFilter(): RagRetrievalFilter {
        return RagRetrievalFilter(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workTypeId = workTypeId,
            recordId = recordId,
            periodStart = periodStart,
            periodEnd = periodEnd
        )
    }

    private fun toStructuredResult(
        answerText: String,
        audit: RagAuditResult,
        chunks: List<RagEvidenceChunk>
    ): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = answerText,
            riskLevel = CoachingRiskLevel.UNKNOWN,
            confidence = 0.0,
            observations = emptyList(),
            diagnosis = answerText,
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = toCitationRefs(audit, chunks)
        )
    }

    private fun toCitationRefs(audit: RagAuditResult, chunks: List<RagEvidenceChunk>): List<CoachingCitationRef> {
        val chunkById = chunks.associateBy { it.id.toString() }
        return audit.citations.distinct().mapNotNull { citation ->
            chunkById[citation]?.let { chunk ->
                CoachingCitationRef(
                    chunkId = citation,
                    label = chunk.label,
                    sourceType = chunk.sourceType
                )
            }
        }
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
