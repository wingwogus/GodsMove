package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RagCitationAuditorTest {
    private val knownChunkId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val lowChunkId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val chunks = listOf(
        RagEvidenceChunk(
            id = knownChunkId,
            sourceType = RagSourceType.TECH_DOCUMENT,
            sourceId = "tech-page-12",
            content = "과습은 뿌리 생육을 저해한다.",
            label = "기술문서 p.12",
            similarityScore = 0.8
        ),
        RagEvidenceChunk(
            id = lowChunkId,
            sourceType = RagSourceType.TECH_DOCUMENT,
            sourceId = "tech-page-13",
            content = "배수 상태를 확인한다.",
            label = "기술문서 p.13",
            similarityScore = 0.4
        )
    )

    @Test
    fun `audit passes when all citations are known`() {
        val audit = RagCitationAuditor().audit(
            answerText = "배수 확인이 필요합니다. [chunk:$knownChunkId]",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.PASS)
        assertThat(audit.warnings).isEmpty()
        assertThat(audit.citations).containsExactly(knownChunkId.toString())
    }

    @Test
    fun `audit fails for missing citations`() {
        val audit = RagCitationAuditor().audit(
            answerText = "배수 확인이 필요합니다.",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("no_citations")
    }

    @Test
    fun `audit warns for low similarity citations`() {
        val audit = RagCitationAuditor().audit(
            answerText = "관련성은 낮지만 참고할 수 있습니다. [chunk:$lowChunkId]",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.WARN)
        assertThat(audit.warnings).contains("low_similarity:$lowChunkId")
    }

    @Test
    fun `extractive fallback cites retrieved chunks`() {
        val answer = RagCitationAuditor().buildExtractiveFallbackAnswer(
            question = "과습 위험이 있을까?",
            retrievedChunks = chunks
        )

        assertThat(answer).contains("[chunk:$knownChunkId]")
        assertThat(answer).contains("모델 답변이 citation 규칙을 지키지 않아")
    }
}
