package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.coaching.rag.common.RagModelInfo
import com.chamchamcham.application.coaching.rag.common.RagProperties
import com.chamchamcham.application.coaching.rag.common.RagSourceType
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

data class GeneratedRecordFeedback(
    val result: RecordFeedbackCoachingResult,
    val citations: List<Map<String, Any?>>,
    val auditWarnings: List<String>,
    val modelInfo: RagModelInfo,
)

enum class RecordFeedbackGenerationFailureCode {
    INSUFFICIENT_EVIDENCE,
    STRUCTURED_OUTPUT_INVALID,
    GENERATION_FAILED,
}

class RecordFeedbackGenerationException(
    val code: RecordFeedbackGenerationFailureCode,
    cause: Throwable? = null,
) : RuntimeException(code.name, cause)

@Service
class RecordFeedbackGenerationService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextValidator: RecordFeedbackContextValidator,
    private val queryPlanner: RecordFeedbackRetrievalQueryPlanner,
    private val promptBuilder: RecordFeedbackPromptBuilder,
    private val outputValidator: RecordFeedbackOutputValidator,
    private val ragProperties: RagProperties,
) {
    fun generate(context: RecordFeedbackContext, topK: Int? = null): GeneratedRecordFeedback {
        val contextValidation = try {
            contextValidator.requireValid(context)
        } catch (exception: RuntimeException) {
            throw RecordFeedbackGenerationException(
                RecordFeedbackGenerationFailureCode.GENERATION_FAILED,
                exception,
            )
        }
        val perQueryTopK = normalizeTopK(topK)
        val queries = queryPlanner.plan(context)
        val documents = retrieveDocuments(queries, perQueryTopK, context.crop.name.trim())
            .filter { it.isCitableOfficialEvidence() }

        if (documents.isEmpty()) {
            throw RecordFeedbackGenerationException(RecordFeedbackGenerationFailureCode.INSUFFICIENT_EVIDENCE)
        }

        val evidence = documents.map { it.toRecordFeedbackEvidence() }
        val prompt = promptBuilder.build(context, queries, evidence)
        val result = callForValidatedResult(prompt, context, evidence)

        return GeneratedRecordFeedback(
            result = result,
            citations = buildCitationMaps(result, context, documents),
            auditWarnings = contextValidation.warnings,
            modelInfo = modelInfo(),
        )
    }

    private fun callForValidatedResult(
        prompt: RecordFeedbackPrompt,
        context: RecordFeedbackContext,
        evidence: List<RecordFeedbackEvidence>,
    ): RecordFeedbackCoachingResult {
        var lastFailure: Throwable? = null
        repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS) {
            val result = try {
                requestStructuredResult(prompt)
            } catch (exception: StructuredOutputFailure) {
                lastFailure = exception
                return@repeat
            } catch (exception: ChatRuntimeFailure) {
                throw RecordFeedbackGenerationException(
                    RecordFeedbackGenerationFailureCode.GENERATION_FAILED,
                    exception.cause,
                )
            }

            val allowedEvidenceRefs = outputValidator.allowedEvidenceRefs(context, evidence)
            val validation = outputValidator.validate(result, allowedEvidenceRefs)
            if (validation.isValid) {
                return result
            }
            lastFailure = StructuredOutputFailure("invalid product output: ${validation.warnings.joinToString(",")}")
        }

        throw RecordFeedbackGenerationException(
            RecordFeedbackGenerationFailureCode.STRUCTURED_OUTPUT_INVALID,
            lastFailure,
        )
    }

    private fun requestStructuredResult(prompt: RecordFeedbackPrompt): RecordFeedbackCoachingResult {
        val callResponse = try {
            chatClient.prompt()
                .system(prompt.system)
                .user(prompt.user)
                .call()
        } catch (exception: RuntimeException) {
            throw ChatRuntimeFailure(exception)
        }

        return try {
            callResponse.entity(RecordFeedbackCoachingResult::class.java)
                ?: throw StructuredOutputFailure("empty structured output")
        } catch (exception: StructuredOutputFailure) {
            throw exception
        } catch (exception: RuntimeException) {
            throw StructuredOutputFailure("structured output parse failed", exception)
        }
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw RecordFeedbackGenerationException(RecordFeedbackGenerationFailureCode.GENERATION_FAILED)
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
            throw RecordFeedbackGenerationException(
                RecordFeedbackGenerationFailureCode.GENERATION_FAILED,
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
        result: RecordFeedbackCoachingResult,
        context: RecordFeedbackContext,
        documents: List<Document>,
    ): List<Map<String, Any?>> {
        val documentsById = documents.associateBy { it.id }
        return result.evidenceRefsInOrder()
            .mapNotNull { citationId ->
                when {
                    citationId == context.recordCitationId() -> recordCitationMap(citationId)
                    citationId.startsWith("weather:") -> weatherCitationMap(citationId)
                    citationId in documentsById -> documentsById.getValue(citationId).documentCitationMap()
                    else -> null
                }
            }
    }

    private fun RecordFeedbackCoachingResult.evidenceRefsInOrder(): List<String> {
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
