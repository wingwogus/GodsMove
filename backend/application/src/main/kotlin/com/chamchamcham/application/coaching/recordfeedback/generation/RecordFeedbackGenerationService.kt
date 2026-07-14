package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.coaching.common.RagSourceType
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class RecordFeedbackGenerationService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val queryPlanner: RecordFeedbackRetrievalQueryPlanner,
    private val promptBuilder: RecordFeedbackPromptBuilder,
    private val ragProperties: RagProperties,
) {
    fun generate(context: RecordFeedbackContext, topK: Int? = null): RecordFeedbackGenerationResult {
        val contextWarnings = requireValidContext(context)
        val perQueryTopK = normalizeTopK(topK)
        val queries = queryPlanner.plan(context)
        val documents = retrieveDocuments(queries, perQueryTopK, context.crop.name.trim())
            .filter { it.isCitableOfficialEvidence() }

        if (documents.isEmpty()) {
            throw RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
        }

        val evidence = documents.map { it.toRecordFeedbackEvidence() }
        val prompt = promptBuilder.build(context, queries, evidence)
        val content = callForValidatedContent(prompt, context, evidence)

        return RecordFeedbackGenerationResult(
            content = content,
            citations = buildCitationMaps(content, context, documents),
            auditWarnings = contextWarnings,
            modelInfo = modelInfo(),
        )
    }

    private fun callForValidatedContent(
        prompt: RecordFeedbackPrompt,
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>,
    ): RecordFeedbackContent {
        var lastFailure: Throwable? = null
        var retryValidationWarnings = emptyList<String>()
        repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS) {
            val content = try {
                requestStructuredContent(prompt.withValidationRetryInstructions(retryValidationWarnings))
            } catch (exception: StructuredOutputFailure) {
                lastFailure = exception
                return@repeat
            } catch (exception: ChatRuntimeFailure) {
                throw RecordFeedbackGenerationFailure(
                    RecordFeedbackFailureCode.CHAT_UNAVAILABLE,
                    exception.cause,
                )
            }

            val validationWarnings = RecordFeedbackOutputValidator.validate(content, context, evidence)
            if (validationWarnings.isEmpty()) {
                return content
            }
            retryValidationWarnings = validationWarnings
            lastFailure = StructuredOutputFailure("invalid product output: ${validationWarnings.joinToString(",")}")
        }

        throw RecordFeedbackGenerationFailure(
            RecordFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID,
            lastFailure,
        )
    }

    private fun requestStructuredContent(prompt: RecordFeedbackPrompt): RecordFeedbackContent {
        val callResponse = try {
            chatClient.prompt()
                .system(prompt.system)
                .user(prompt.user)
                .call()
        } catch (exception: RuntimeException) {
            throw ChatRuntimeFailure(exception)
        }

        return try {
            callResponse.entity(RecordFeedbackContent::class.java)
                ?: throw StructuredOutputFailure("empty structured output")
        } catch (exception: StructuredOutputFailure) {
            throw exception
        } catch (exception: BusinessException) {
            throw ChatRuntimeFailure(exception)
        } catch (exception: RuntimeException) {
            throw StructuredOutputFailure("structured output parse failed", exception)
        }
    }

    private fun RecordFeedbackPrompt.withValidationRetryInstructions(
        validationWarnings: List<String>,
    ): RecordFeedbackPrompt {
        if (validationWarnings.isEmpty()) {
            return this
        }

        return copy(
            user = "$user\n\n" +
                "직전 응답은 내부 검증을 통과하지 못했습니다. " +
                "다음 오류를 모두 고친 완전한 JSON만 다시 반환하세요:\n" +
                validationWarnings.joinToString("\n") { "- $it" },
        )
    }

    private fun requireValidContext(context: RecordFeedbackContext): List<String> {
        val errors = mutableListOf<String>()

        if (context.schemaVersion != RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION) {
            errors += "invalid_schema_version"
        }
        if (context.farm.name.isBlank()) {
            errors += "farm_name_blank"
        }
        if (context.farm.roadAddress.isBlank()) {
            errors += "farm_road_address_blank"
        }
        if (context.crop.name.isBlank()) {
            errors += "crop_name_blank"
        }
        if (context.record.memo.isBlank()) {
            errors += "record_memo_blank"
        }
        if (context.record.photoCount < 0) {
            errors += "photo_count_negative"
        }

        if (errors.isNotEmpty()) {
            throw RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INVALID_CONTEXT)
        }
        return context.warnings.distinct()
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INVALID_GENERATION_REQUEST)
        }
        return value
    }

    private fun retrieveDocuments(
        queries: List<RecordFeedbackRetrievalQuery>,
        perQueryTopK: Int,
        cropName: String,
    ): List<Document> {
        val safeCropName = cropName.replace("'", "")
        return try {
            queries
                .flatMap { query ->
                    vectorStore.similaritySearch(
                        SearchRequest.builder()
                            .query(query.query)
                            .topK(perQueryTopK)
                            .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
                            .filterExpression(
                                "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' && " +
                                    "cropName in ['$safeCropName', '$GENERAL_CROP_NAME']",
                            )
                            .build(),
                    )
                }
                .filter { it.metadata["sourceType"] == RagSourceType.TECH_DOCUMENT.name }
                .distinctBy { it.id }
        } catch (exception: RuntimeException) {
            throw RecordFeedbackGenerationFailure(
                RecordFeedbackFailureCode.RETRIEVAL_FAILED,
                exception,
            )
        }
    }

    private fun Document.isCitableOfficialEvidence(): Boolean {
        return metadata["sourceType"] == RagSourceType.TECH_DOCUMENT.name &&
            id.isNotBlank() &&
            !text.isNullOrBlank()
    }

    private fun Document.toRecordFeedbackEvidence(): RecordFeedbackEvidence {
        return RecordFeedbackEvidence(
            id = id,
            title = documentTitle() ?: id,
            page = page(),
            content = text ?: "",
        )
    }

    private fun buildCitationMaps(
        content: RecordFeedbackContent,
        context: RecordFeedbackContext,
        documents: List<Document>,
    ): List<Map<String, Any?>> {
        val documentsById = documents.associateBy { it.id }
        return content.evidenceRefsInOrder()
            .mapNotNull { citationId ->
                when {
                    citationId == context.recordCitationId() -> recordCitationMap(citationId)
                    citationId.startsWith("weather:") -> weatherCitationMap(citationId)
                    citationId in documentsById -> documentsById.getValue(citationId).documentCitationMap()
                    else -> null
                }
            }
    }

    private fun RecordFeedbackContent.evidenceRefsInOrder(): List<String> {
        return buildList {
            addAll(goodPoint.evidenceRefs)
            nextActions.forEach { addAll(it.evidenceRefs) }
        }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun recordCitationMap(citationId: String): Map<String, Any?> {
        return linkedMapOf(
            "id" to citationId,
            "title" to "대상 영농기록",
            "sourceType" to RagSourceType.FARMING_RECORD.name,
        )
    }

    private fun weatherCitationMap(citationId: String): Map<String, Any?> {
        return linkedMapOf(
            "id" to citationId,
            "title" to "날씨 context",
            "sourceType" to "WEATHER",
        )
    }

    private fun Document.documentCitationMap(): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "title" to documentTitle(),
            "page" to page(),
            "source" to sourceFileName(),
            "sourceType" to RagSourceType.TECH_DOCUMENT.name,
        )
    }

    private fun Document.documentTitle(): String? {
        return metadata["documentTitle"]?.toString()
            ?: metadata["label"]?.toString()
    }

    private fun Document.page(): Int? {
        return metadata["page"]?.toString()?.toIntOrNull()
    }

    private fun Document.sourceFileName(): String? {
        val path = metadata["pdfPath"]?.toString() ?: return null
        return path.substringAfterLast('/').substringAfterLast('\\').ifBlank { null }
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model,
        )
    }

    private class StructuredOutputFailure(
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)

    private class ChatRuntimeFailure(cause: Throwable) : RuntimeException(cause)

    companion object {
        const val GENERAL_CROP_NAME = "GENERAL"
        const val MAX_STRUCTURED_OUTPUT_ATTEMPTS = 2
    }
}
