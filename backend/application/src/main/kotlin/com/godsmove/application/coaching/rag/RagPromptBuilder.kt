package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class RagPromptBuilder {
    fun buildPrompt(question: String, chunks: List<RagEvidenceChunk>): List<ChatMessage> {
        val evidence = formatEvidence(chunks)
        return listOf(
            ChatMessage(
                role = "system",
                content = "너는 농업 영농 코칭 보조자다. 반드시 제공된 근거 chunk만 사용해 한국어로 답한다. " +
                    "모든 근거 문장 끝에 [chunk:<id>] citation을 붙인다. 근거가 부족하면 현재 자료만으로는 판단할 수 없다고 말한다."
            ),
            ChatMessage(
                role = "user",
                content = "질문: $question\n\n근거:\n$evidence"
            )
        )
    }

    fun buildCitationRetryPrompt(question: String, chunks: List<RagEvidenceChunk>): List<ChatMessage> {
        val allowed = chunks.joinToString(", ") { "[chunk:${it.id}]" }
        return listOf(
            ChatMessage(
                role = "system",
                content = "이전 답변은 citation 감사에 실패했다. 허용된 chunk id만 사용하고 모든 bullet 끝에 정확한 citation을 붙인다."
            ),
            ChatMessage(
                role = "user",
                content = "질문: $question\n\n허용된 citation: $allowed\n\n근거:\n${formatEvidence(chunks)}"
            )
        )
    }

    private fun formatEvidence(chunks: List<RagEvidenceChunk>): String {
        return chunks.joinToString("\n\n") { chunk ->
            "[chunk:${chunk.id}] ${chunk.label}\n${chunk.content}"
        }
    }
}
