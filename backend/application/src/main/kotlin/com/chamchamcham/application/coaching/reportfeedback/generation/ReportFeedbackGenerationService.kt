package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.coaching.common.RagSourceType
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class ReportFeedbackGenerationService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val queryPlanner: ReportFeedbackRetrievalQueryPlanner,
    private val promptBuilder: ReportFeedbackPromptBuilder,
    private val ragProperties: RagProperties,
) {
    fun generate(context: ReportFeedbackContext): ReportFeedbackGenerationResult {
        validateContext(context)
        val documents = retrieveDocuments(context)
        val evidence = documents.map {
            ReportFeedbackEvidence(
                id = it.id,
                title = it.metadata["documentTitle"]?.toString() ?: it.id,
                content = it.text ?: "",
            )
        }
        val prompt = promptBuilder.build(context, evidence)
        val content = requestValidatedContent(prompt, context, evidence)
        return ReportFeedbackGenerationResult(
            content = content,
            citations = citations(content, context, documents),
            auditWarnings = context.warnings.distinct(),
            modelInfo = RagModelInfo(
                embedding = ragProperties.embedding.model,
                chat = ragProperties.chat.model,
            ),
        )
    }

    private fun validateContext(context: ReportFeedbackContext) {
        val recordCount = (context.report.statistics["recordCount"] as? Number)?.toInt()
        if (
            context.schemaVersion != REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION ||
            context.report.farmName.isBlank() ||
            context.report.cropName.isBlank() ||
            context.records.isEmpty() ||
            context.records.any { it.workType != context.workType } ||
            recordCount == null ||
            recordCount <= 0
        ) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
        }
    }

    private fun retrieveDocuments(context: ReportFeedbackContext): List<Document> {
        val cropName = context.report.cropName.replace("'", "")
        return try {
            queryPlanner.plan(context)
                .flatMap { query ->
                    vectorStore.similaritySearch(
                        SearchRequest.builder()
                            .query(query)
                            .topK(ragProperties.retrieval.topKDefault)
                            .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
                            .filterExpression(
                                "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' && " +
                                    "cropName in ['$cropName', '$GENERAL_CROP_NAME']",
                            )
                            .build(),
                    )
                }
                .filter { it.metadata["sourceType"] == RagSourceType.TECH_DOCUMENT.name }
                .filter { it.id.isNotBlank() && !it.text.isNullOrBlank() }
                .distinctBy(Document::getId)
        } catch (exception: RuntimeException) {
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.RETRIEVAL_FAILED, exception)
        }
    }

    private fun requestValidatedContent(
        prompt: ReportFeedbackPrompt,
        context: ReportFeedbackContext,
        evidence: List<ReportFeedbackEvidence>,
    ): ReportFeedbackContent {
        var lastFailure: Throwable? = null
        var retryWarnings = emptyList<String>()
        repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS) {
            val attemptPrompt = prompt.withValidationRetryInstructions(retryWarnings)
            val response = try {
                chatClient.prompt().system(attemptPrompt.system).user(attemptPrompt.user).call()
            } catch (exception: RuntimeException) {
                throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE, exception)
            }
            val content = try {
                response
                    .entity(ReportFeedbackContent::class.java)
                    ?: throw IllegalStateException("empty structured output")
            } catch (exception: RuntimeException) {
                lastFailure = exception
                retryWarnings = listOf("structured_output_parse_failed")
                return@repeat
            }
            val warnings = ReportFeedbackOutputValidator.validate(content, context, evidence)
            if (warnings.isEmpty()) {
                return content
            }
            retryWarnings = warnings.toSafeRetryWarnings()
            lastFailure = IllegalStateException(retryWarnings.joinToString(","))
        }
        throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID, lastFailure)
    }

    private fun ReportFeedbackPrompt.withValidationRetryInstructions(
        warnings: List<String>,
    ): ReportFeedbackPrompt {
        if (warnings.isEmpty()) return this
        return copy(
            user = "$user\n\n" +
                "직전 응답은 내부 검증을 통과하지 못했습니다. 다음 오류를 모두 고친 완전한 JSON만 다시 반환하세요:\n" +
                warnings.joinToString("\n") { "- $it" },
        )
    }

    private fun List<String>.toSafeRetryWarnings(): List<String> = map { warning ->
        when {
            warning.startsWith("unknown_evidence:") -> "unknown_evidence"
            warning in SAFE_RETRY_WARNINGS -> warning
            SAFE_ITEM_WARNING.matches(warning) -> warning
            else -> "invalid_output"
        }
    }.distinct()

    private fun citations(
        content: ReportFeedbackContent,
        context: ReportFeedbackContext,
        documents: List<Document>,
    ): List<Map<String, Any?>> {
        val documentsById = documents.associateBy(Document::getId)
        return content.items().flatMap { it.item.evidenceRefs }
            .distinct()
            .mapNotNull { ref ->
                when {
                    ref.startsWith("record:") -> mapOf("id" to ref, "sourceType" to RagSourceType.FARMING_RECORD.name)
                    ref == "report:${context.previousReport?.id}" -> mapOf("id" to ref, "sourceType" to "FARMING_REPORT")
                    ref in documentsById -> mapOf(
                        "id" to ref,
                        "sourceType" to RagSourceType.TECH_DOCUMENT.name,
                        "title" to documentsById.getValue(ref).metadata["documentTitle"],
                    )
                    else -> null
                }
            }
    }

    private companion object {
        const val GENERAL_CROP_NAME = "GENERAL"
        const val MAX_STRUCTURED_OUTPUT_ATTEMPTS = 2
        val SAFE_ITEM_WARNING = Regex("^(strength|improvement|next_action)_(basis_blank|text_blank|text_tone|evidence_refs_blank)$")
        val SAFE_RETRY_WARNINGS = setOf(
            "summary_blank",
            "summary_text_tone",
            "duplicate_item",
            "structured_output_parse_failed",
        )
    }
}
