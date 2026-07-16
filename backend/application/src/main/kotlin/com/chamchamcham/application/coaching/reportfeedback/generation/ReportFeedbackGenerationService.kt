package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.coaching.common.RagSourceType
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.exception.business.BusinessException
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
        var retryInstructions = emptyList<String>()
        repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS) {
            val attemptPrompt = prompt.withValidationRetryInstructions(retryInstructions)
            val response = try {
                chatClient.prompt().system(attemptPrompt.system).user(attemptPrompt.user).call()
            } catch (exception: RuntimeException) {
                throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE, exception)
            }
            val content = try {
                response
                    .entity(ReportFeedbackContent::class.java)
                    ?.normalizedParagraphs()
                    ?: throw IllegalStateException("empty structured output")
            } catch (exception: BusinessException) {
                throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE, exception)
            } catch (_: RuntimeException) {
                val diagnostics = listOf("structured_output_parse_failed")
                retryInstructions = diagnostics.toRetryInstructions(content = null)
                lastFailure = IllegalStateException(diagnostics.single())
                return@repeat
            }
            val warnings = ReportFeedbackOutputValidator.validate(content, context, evidence)
            if (warnings.isEmpty()) {
                return content
            }
            val diagnostics = warnings.toSafeRetryWarnings()
            retryInstructions = diagnostics.toRetryInstructions(content)
            lastFailure = IllegalStateException(diagnostics.joinToString(","))
        }
        throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID, lastFailure)
    }

    private fun ReportFeedbackPrompt.withValidationRetryInstructions(
        instructions: List<String>,
    ): ReportFeedbackPrompt {
        if (instructions.isEmpty()) return this
        return copy(
            user = "$user\n\n" +
                "직전 응답은 내부 검증을 통과하지 못했습니다. " +
                "다음 지시를 모두 반영한 완전한 JSON만 다시 반환하세요:\n" +
                instructions.joinToString("\n") { "- $it" },
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

    private fun List<String>.toRetryInstructions(
        content: ReportFeedbackContent?,
    ): List<String> = map { it.toRetryInstruction(content) }.distinct()

    private fun String.toRetryInstruction(content: ReportFeedbackContent?): String = when (this) {
        "summary_blank" ->
            "summary에 이번 재배에서 확인한 핵심을 공백과 문장부호를 포함해 20~65자로 작성하세요."
        "summary_text_length" -> lengthRetryInstruction(
            path = "summary",
            text = content?.summary,
            role = "이번 재배에서 확인한 핵심을",
        )
        "comparison_not_available" ->
            "서버가 제공한 지난 재배 비교가 없으므로 comparisons는 빈 배열로 반환하세요."
        "comparison_current_report_ref_required" ->
            "comparisons[0].evidenceRefs에 현재 리포트 근거를 허용 evidenceRefs에서 선택해 포함하세요."
        "comparison_previous_report_ref_required" ->
            "comparisons[0].evidenceRefs에 지난 재배 리포트 근거를 허용 evidenceRefs에서 선택해 포함하세요."
        "structured_output_parse_failed" ->
            "설명이나 Markdown 없이 JSON Schema의 필드명과 타입을 그대로 따른 완전한 JSON만 반환하세요. " +
                "summary는 문자열이고 comparisons, strengths, improvements, nextActions는 배열입니다."
        "unknown_evidence" -> "모든 evidenceRefs에는 허용 evidenceRefs에 나열된 값만 사용하세요."
        "invalid_output" -> GENERIC_RETRY_INSTRUCTION
        else -> SAFE_ITEM_WARNING.matchEntire(this)?.let { match ->
            itemRetryInstruction(
                section = match.groupValues[1],
                violation = match.groupValues[2],
                content = content,
            )
        } ?: GENERIC_RETRY_INSTRUCTION
    }

    private fun itemRetryInstruction(
        section: String,
        violation: String,
        content: ReportFeedbackContent?,
    ): String {
        val (field, items, role) = when (section) {
            "comparison" -> Triple("comparisons", content?.comparisons, "지난 재배와 달라진 사실을")
            "strength" -> Triple("strengths", content?.strengths, "잘한 행동과 도움이 된 이유를")
            "improvement" -> Triple("improvements", content?.improvements, "부족한 점, 영향, 보완 방향을")
            "next_action" -> Triple("nextActions", content?.nextActions, "언제 무엇을 할지 한 가지 행동을")
            else -> return GENERIC_RETRY_INSTRUCTION
        }
        return when (violation) {
            "count" -> "$field 배열은 정확히 1개로 작성하세요."
            "basis_blank" -> "$field[0].basis에 판단 근거를 작성하세요."
            "text_blank" ->
                "$field[0].text에 $role 공백과 문장부호를 포함해 20~65자로 작성하세요."
            "text_length" -> lengthRetryInstruction("$field[0].text", items?.singleOrNull()?.text, role)
            "text_paragraph" -> "$field[0].text를 줄바꿈이나 목록 기호 없이 한 문단으로 작성하세요."
            "evidence_refs_blank" ->
                "$field[0].evidenceRefs에 허용 evidenceRefs의 식별자를 하나 이상 포함하세요."
            else -> GENERIC_RETRY_INSTRUCTION
        }
    }

    private fun lengthRetryInstruction(
        path: String,
        text: String?,
        role: String,
    ): String {
        if (text == null) {
            return "${path}에 $role 공백과 문장부호를 포함해 20~65자로 작성하세요."
        }
        val direction = when {
            text.length > MAX_RETRY_TEXT_LENGTH -> "줄이세요"
            text.length < MIN_RETRY_TEXT_LENGTH -> "늘리세요"
            else -> "다시 작성하세요"
        }
        return "${path}는 현재 ${text.length}자입니다. $role 유지하면서 " +
            "공백과 문장부호를 포함해 20~65자로 $direction."
    }

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
                    ref == "report:${context.report.id}" -> mapOf("id" to ref, "sourceType" to "FARMING_REPORT")
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
        const val MIN_RETRY_TEXT_LENGTH = 20
        const val MAX_RETRY_TEXT_LENGTH = 65
        const val GENERIC_RETRY_INSTRUCTION =
            "JSON의 모든 필드, 항목 수, 글자 수와 evidenceRefs 조건을 다시 확인하세요."
        val SAFE_ITEM_WARNING = Regex(
            "^(comparison|strength|improvement|next_action)_" +
                "(count|basis_blank|text_blank|text_length|text_paragraph|evidence_refs_blank)$",
        )
        val SAFE_RETRY_WARNINGS = setOf(
            "summary_blank",
            "summary_text_length",
            "comparison_not_available",
            "comparison_current_report_ref_required",
            "comparison_previous_report_ref_required",
            "structured_output_parse_failed",
        )
    }
}
