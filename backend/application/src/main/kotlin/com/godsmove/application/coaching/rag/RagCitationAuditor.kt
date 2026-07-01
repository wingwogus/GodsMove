package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class RagCitationAuditor {
    private val citationPattern = Regex("\\[chunk:([^\\]]+)]")

    fun audit(
        answerText: String,
        retrievedChunks: List<RagEvidenceChunk>,
        lowSimilarityThreshold: Double
    ): RagAuditResult {
        val warnings = mutableListOf<String>()
        val citations = extractCitations(answerText)
        val retrievedById = retrievedChunks.associateBy { it.id.toString() }

        if (retrievedChunks.isEmpty()) warnings += "no_retrieved_chunks"
        if (answerText.isBlank()) warnings += "empty_answer"
        if (answerText.isNotBlank() && citations.isEmpty()) warnings += "no_citations"

        citations.forEach { citation ->
            val chunk = retrievedById[citation]
            when {
                chunk == null -> warnings += "unknown_citation:$citation"
                chunk.similarityScore < lowSimilarityThreshold -> warnings += "low_similarity:$citation"
            }
        }

        val hasFailure = warnings.any {
            it == "no_retrieved_chunks" ||
                it == "empty_answer" ||
                it == "no_citations" ||
                it.startsWith("unknown_citation:")
        }

        return RagAuditResult(
            status = if (hasFailure) {
                RagAuditStatus.FAIL
            } else if (warnings.isNotEmpty()) {
                RagAuditStatus.WARN
            } else {
                RagAuditStatus.PASS
            },
            warnings = warnings.distinct(),
            citations = citations
        )
    }

    fun shouldRetry(audit: RagAuditResult): Boolean {
        return audit.warnings.any { it == "no_citations" || it.startsWith("unknown_citation:") }
    }

    fun buildExtractiveFallbackAnswer(
        question: String,
        retrievedChunks: List<RagEvidenceChunk>,
        maxChunks: Int = 4
    ): String {
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(question)
            .map { it.value }
            .filter { it.length >= 2 }
            .toSet()
        val bullets = retrievedChunks.take(maxChunks).map { chunk ->
            "- ${chunk.label}: ${snippet(chunk.content, tokens)} [chunk:${chunk.id}]"
        }

        return listOf(
            "모델 답변이 citation 규칙을 지키지 않아, 검색된 근거에서 확인 가능한 내용만 추출했습니다.",
            "",
            *bullets.toTypedArray()
        ).joinToString("\n")
    }

    private fun extractCitations(text: String): List<String> {
        return citationPattern.findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun snippet(content: String, tokens: Set<String>, maxLength: Int = 220): String {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= maxLength) return normalized
        val index = tokens.mapNotNull { token ->
            normalized.indexOf(token, ignoreCase = true).takeIf { it >= 0 }
        }.minOrNull() ?: 0
        val start = (index - 70).coerceAtLeast(0)
        val prefix = if (start > 0) "..." else ""
        return prefix + normalized.substring(start, (start + maxLength).coerceAtMost(normalized.length)).trim()
    }
}
